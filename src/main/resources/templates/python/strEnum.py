class StrEnum(str, Enum):
    """Enum where members are also (and must be) strings."""

    def __new__(cls, *values):
        """Values must already be of type `str`"""
        value = str(*values)
        member = str.__new__(cls, value)
        member._value_ = value
        return member

    def __str__(self):
        return self._value_
