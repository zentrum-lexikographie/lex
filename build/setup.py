from setuptools import setup, find_packages

setup(
    name="zdl_lex_build",
    packages=find_packages(),
    install_requires=[
        'Click==7.0',
        'gitpython==3.0.4',
        'docker==4.1.0'
    ],
    entry_points='''
        [console_scripts]
        zdl-lex-build=zdl_lex_build.cli:main
        zdl-lex-clojure=zdl_lex_build.clj:main
    '''
)
