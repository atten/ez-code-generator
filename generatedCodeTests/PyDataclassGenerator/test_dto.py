def test_import():
    from generated.dto import AdvancedDto  # noqa


def test_property():
    from generated.dto import AdvancedDto

    dto = AdvancedDto(a=5, b=10)
    assert dto.sum == 15
