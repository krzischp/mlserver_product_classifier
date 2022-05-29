from .fake import FakeRuntime
from .bert import BertRuntime

__all__ = ["FakeRuntime", "BertRuntime"]
# O que esse código faz é remover o nome do arquivo fake.py da referência ao módulo custom-runtimes, 
# tornando a configuração um pouco mais limpa e intuitiva
