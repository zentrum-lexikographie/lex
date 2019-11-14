from sqlalchemy import create_engine


_engine = create_engine(
    'mysql+pymysql://dwdswb:dwdswb@localhost/dwdswb',
    echo=True
)

print(list(_engine.execute('SELECT 1 + 1 FROM dual')))
