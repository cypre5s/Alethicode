"""NFK 训练数据 IO 与契约校验。

子模块从外层显式导入，例如::

    from nfk.data.contract_validator import validate_csv, NfkContractError

不在本 ``__init__`` 做 re-export，避免 ``python -m nfk.data.contract_validator``
触发 ``RuntimeWarning: 'nfk.data.contract_validator' found in sys.modules``。
"""
