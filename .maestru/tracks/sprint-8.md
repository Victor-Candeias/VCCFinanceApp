---
maestru: "0.4"
type: work-track
id: sprint-8
title: Sprint 8 — Compras
created: 2026-10-23
description: "Compras de vários tipos (calculadora, listas planeadas, despesas rápidas, scanner/OCR) inspiradas no VCCSmartShopcart, pagas por MB (conta bancária) ou Dinheiro (conta CASH) e refletidas nos movimentos."
owner: developer
status: draft
---

# sprint-8: Sprint 8 — Compras

## Summary

<!-- maestru:work-items-list -->
| ID | Title | Status | Created | Owner | Priority | Completed | Template | Blocked By | Spec |
|---|---|---|---|---|---|---|---|---|---|
| S8-01 | Modelo de domínio e Room das Compras (migração 4→5) | done | 2026-10-23 |  | high | 2026-10-23 |  |  |  |
| S8-02 | Pagamento MB/Dinheiro e integração atómica com Movimentos | done | 2026-10-23 |  | high | 2026-10-23 |  | sprint-8/S8-01 |  |
| S8-03 | Hub de Compras, navegação, estabelecimentos e catálogo de produtos | done | 2026-10-23 |  | high | 2026-10-23 |  | sprint-8/S8-01 |  |
| S8-04 | Compra rápida (calculadora): itens, quantidades, descontos, orçamento e conclusão com pagamento | done | 2026-10-23 |  | high | 2026-10-23 |  | sprint-8/S8-02, sprint-8/S8-03 |  |
| S8-05 | Listas planeadas e compra a partir de lista | done | 2026-10-23 |  | medium | 2026-10-23 |  | sprint-8/S8-04 |  |
| S8-06 | Despesas rápidas com catálogo, favoritos, produtos temporários e rascunho | done | 2026-10-23 |  | medium | 2026-10-23 |  | sprint-8/S8-02, sprint-8/S8-03 |  |
| S8-07 | Histórico de compras: detalhe, editar, eliminar, repetir e sugestões de preço | backlog | 2026-10-23 |  | medium |  |  | sprint-8/S8-04, sprint-8/S8-06 |  |
| S8-08 | Scanner de código de barras offline (CameraX + ML Kit) com catálogo local | backlog | 2026-10-23 |  | medium |  |  | sprint-8/S8-04 |  |
| S8-09 | OCR de preços com confirmação explícita | backlog | 2026-10-23 |  | low |  |  | sprint-8/S8-08 |  |
| S8-10 | Open Food Facts e Open Prices (requer decisão sobre permissão INTERNET) | backlog | 2026-10-23 |  | low |  |  | sprint-8/S8-08 |  |
| S8-11 | Compras em relatórios, orçamentos, exportação e backup | backlog | 2026-10-23 |  | medium |  |  | sprint-8/S8-07 |  |
| S8-12 | Testes de aceitação, acessibilidade, documentação e APK das Compras | backlog | 2026-10-23 |  | medium |  |  | sprint-8/S8-05, sprint-8/S8-11 |  |
<!-- /maestru:work-items-list -->
