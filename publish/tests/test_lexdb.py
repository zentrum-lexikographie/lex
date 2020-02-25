from zdl.lexdb import frequencies


def test_frequencies():
    assert ('Arztekammer' in frequencies('kernbasis', ['Arztekammer']))
