from zdl.cab import lemmatize


def test_lemmatize():
    assert lemmatize(['Ärztekammer']) == {'Ärztekammer': 'Arztekammer'}
