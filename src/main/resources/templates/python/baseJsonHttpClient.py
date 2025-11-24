JSON_PAYLOAD = t.Union[dict, str, int, float, list]
RESPONSE_BODY = [str, io.IOBase]


class BaseJsonHttpClient:
    def __init__(
        self,
        base_url: str,
        logger: t.Union[logging.Logger, t.Callable[[str], None], None],
        max_retries: int,
        retry_timeout: float,
        user_agent: str | None,
        headers: dict[str, str | t.Callable[[], str]] | None,
        use_response_streaming: bool,
        use_debug_curl: bool,
        request_kwargs: dict,
        connection_pool_kwargs: dict,
        exception_class: t.Type[Exception],
    ):
        connection_pool_kwargs.update(retries=False)

        self._pool = urllib3.PoolManager(**connection_pool_kwargs)
        self._base_url = base_url
        self._logger = logger
        self._max_retries = max_retries
        self._retry_timeout = retry_timeout
        self._user_agent = user_agent
        self._headers = headers
        self._use_response_streaming = use_response_streaming
        self._use_debug_curl = use_debug_curl
        self._request_kwargs = request_kwargs
        self._exception_class = exception_class

    def fetch(
        self,
        url: str,
        method: str = 'get',
        query_params: dict | None = None,
        json_body: JSON_PAYLOAD | None = None,
        form_fields: dict[str, str] | None = None,
    ) -> RESPONSE_BODY:
        """
        Retrieve JSON response from remote API request.

        Repeats request in case of network errors.

        :param url: target url (relative to base url)
        :param method: HTTP verb, e.g. get/post
        :param query_params: key-value arguments like ?param1=11&param2=22
        :param json_body: JSON-encoded HTTP body
        :param form_fields: form-encoded HTTP body
        :return: decoded JSON from server
        """
        full_url = self._get_full_url(url, query_params)
        headers = self._build_headers()
        body = None
        if json_body is not None:
            body = json.dumps(json_body).encode('utf8')
            headers['content-type'] = 'application/json'
        if form_fields is not None:
            body = urlencode(form_fields)
            headers['content-type'] = 'application/x-www-form-urlencoded'

        request_kwargs = self._request_kwargs.copy()
        request_kwargs.update(
            url=full_url,
            method=method,
            headers=headers,
            body=body,
        )

        try:
            return failsafe_call(
                self._mk_request,
                kwargs=request_kwargs,
                exceptions=(urllib3.exceptions.HTTPError,),  # include connection errors, HTTP >= 400
                logger=self._logger,
                max_attempts=self._max_retries,
                on_transitional_fail=lambda exc, info: sleep(self._retry_timeout)
            )
        except Exception as e:
            error_verbose = str(e)
            if ' at 0x' in error_verbose:
                # reduce noise in error description, e.g. in case of NewConnectionError
                error_verbose = error_verbose.split(':', maxsplit=1)[-1].strip()
            if self._use_debug_curl:
                curl_cmd = build_curl_command(
                    url=full_url,
                    method=method,
                    headers=headers,
                    body=body,
                )
                raise self._exception_class(f'Failed to {curl_cmd}: {error_verbose}') from e

            raise self._exception_class(f'Failed to {method} {full_url}: {error_verbose}') from e

    def _mk_request(self, *args, **kwargs) -> RESPONSE_BODY:
        response = self._pool.request(*args, **kwargs, preload_content=False)
        if response.status >= 400:
            raise urllib3.exceptions.HTTPError('Server respond with status code {status}: {data}'.format(
                status=response.status,
                data=response.data,
            ))

        if 'json' in response.headers.get('content-type', ''):
            # provide Bytes I/O for file-like JSON read
            return response

        # decode whole non-json response into string
        return response.data.decode()

    def _get_full_url(self, url: str, query_params: dict | None = None) -> str:
        if self._base_url:
            url = urljoin(self._base_url, url)

        if query_params:
            query_tuples = []
            for key, value in query_params.items():
                if isinstance(value, (list, tuple)):
                    for item in value:
                        query_tuples.append((key, item))
                else:
                    query_tuples.append((key, value))

            if '?' in url:
                url += '&' + urlencode(query_tuples)
            else:
                url += '?' + urlencode(query_tuples)

        return url

    def _build_headers(self) -> dict[str, str]:
        """
        Render headers dictionary, convert callable headers into strings (if any).
        """
        headers = {}

        if self._headers:
            for key, value in self._headers.items():
                if callable(value):
                    headers[key] = value()
                else:
                    headers[key] = value

        if self._user_agent:
            headers['user-agent'] = self._user_agent

        return headers