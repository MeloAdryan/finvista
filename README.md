# FinVista — Acompanhamento e análise financeira

O FinVista nasceu para facilitar a visualização dos dados da Conta Azul e o acompanhamento de metas financeiras dos clientes de um escritório de contabilidade de Rio Branco, no Acre.

Com a identificação de novas necessidades,e a pedido dos membros do próprio escritório, o projeto evoluiu para uma plataforma de acompanhamento financeiro. Atualmente, reúne painéis com indicadores, gestão de metas e orçamentos, análises de despesas por categoria e centro de custo, comparações entre períodos e detalhamento das mudanças nos gastos.

## Funcionalidades

A plataforma oferece simulações financeiras de seis meses, permitindo explorar cenários com diferentes expectativas de receita e saldo inicial. Essas simulações dependem das hipóteses informadas e dos dados cadastrados.

Os gráficos possuem recursos de interação, consulta em tabela e exportação de dados, com ampliação já disponível na área de metas.

No ambiente administrativo, os admin podem selecionar e administrar diferentes empresas, cadastrar clientes e usuários e importar dados financeiros por arquivos CSV e Excel.

A integração com a Conta Azul está em desenvolvimento e validação. O processo de importação inclui conferência e controles para reduzir a ocorrência de registros duplicados.

## Tecnologias utilizadas

- Frontend: React e TypeScript.
- Interface e estilos: HTML5 e CSS, com Grid e Flexbox.
- Backend: Java e Spring Boot.
- Banco de dados: PostgreSQL.

## Interface e controle de acesso

O FinVista busca oferecer uma interface clara e responsiva, com adaptação para diferentes tamanhos de tela, organização de títulos e espaçamentos e padronização de cores e componentes.

A plataforma utiliza autenticação, permissões por perfil e separação dos dados por empresa.

## Próximas evoluções

- Central de Alertas financeiros.
- Solicitações de ajuste de orçamento com aprovação administrativa.
- Melhorias na experiência de uso em dispositivos móveis.
