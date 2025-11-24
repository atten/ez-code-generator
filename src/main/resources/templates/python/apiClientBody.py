    @typechecked
    def __init__(
        self,
        base_url: str = '',
        headers: dict[str, str | t.Callable[[], str]] | None = None,
        logger: t.Union[logging.Logger, t.Callable[[str], None], None] = None,
        max_retries: int = int(os.environ.get('API_CLIENT_MAX_RETRIES', 5)),
        retry_timeout: float = float(os.environ.get('API_CLIENT_RETRY_TIMEOUT', 3)),
        user_agent: str | None = os.environ.get('API_CLIENT_USER_AGENT'),
        use_response_streaming = bool(int(os.environ.get('API_CLIENT_USE_STREAMING', 1))),
        use_request_payload_validation: bool = bool(int(os.environ.get('API_CLIENT_USE_REQUEST_PAYLOAD_VALIDATION', 1))),
        use_debug_curl: bool = bool(int(os.environ.get('API_CLIENT_USE_DEBUG_CURL', 0))),
        request_kwargs: dict[str, t.Any] | None = None,
        connection_pool_kwargs: dict[str, t.Any] | None = None,
        exception_class: t.Type[Exception] = RuntimeError,
    ):
        """
        API client constructor and configuration method.

        :param base_url: protocol://url[:port]
        :param headers: dict of HTTP headers (e.g. tokens)
        :param logger: logger instance (or callable like print()) for requests diagnostics
        :param max_retries: number of request attempts before Exception defined in `exception_class` raised
        :param retry_timeout: seconds between attempts
        :param user_agent: request header
        :param use_response_streaming: enable alternative JSON library for deserialization (lower latency and memory footprint)
        :param use_request_payload_validation: enable client-side validation of serialized data before send
        :param use_debug_curl: include curl-formatted data for requests diagnostics
        :param request_kwargs: optional request arguments
        :param connection_pool_kwargs: optional arguments for internal connection pool
        :param exception_class: exception class for irrecoverable API errors
        """
        self._client = BaseJsonHttpClient(
            base_url=base_url,
            logger=logger,
            max_retries=max_retries,
            retry_timeout=retry_timeout,
            user_agent=user_agent,
            headers=headers,
            use_response_streaming=use_response_streaming,
            use_debug_curl=use_debug_curl,
            request_kwargs=request_kwargs or {},
            connection_pool_kwargs=connection_pool_kwargs or {},
            exception_class=exception_class,
        )

        self._deserializer = BaseDeserializer(
            use_response_streaming=use_response_streaming
        )

        self._serializer = BaseSerializer(
            self._deserializer,
            use_request_payload_validation=use_request_payload_validation
        )
