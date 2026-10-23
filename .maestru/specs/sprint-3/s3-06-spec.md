---
maestru: "0.4"
type: work-spec
id: s3-06-spec
title: "S3-06 Plano de implementação"
template: implementation-plan-v1
work-item: sprint-3/S3-06
owner: developer
created: 2026-10-23
---

# S3-06 Plano de implementação

## Overview

- **Dinheiro**: reutiliza `AccountType.CASH` (já existente); o saldo aparece em Contas/Dashboard como qualquer conta.
- **Levantamento**: transferência interna (S3-04) de uma conta para uma conta Dinheiro — altera os saldos
  das duas contas sem contar como receita/despesa. Nova entrada "Levantamento" no menu "+" dos Movimentos,
  que abre o editor de transferências em modo levantamento (destino = primeira conta Dinheiro ativa,
  origem = conta por omissão ou primeira conta que não seja Dinheiro, descrição "Levantamento").
- **Conta por omissão**: novo campo `accounts.isDefault` (só uma conta pode estar marcada). Definido no
  editor da conta; a conta fica pré-selecionada ao criar receitas/despesas. Desativar a conta limpa a marca.
  Por estar na base de dados, entra automaticamente no backup encriptado.

## Implementation

1. Room v3 → v4: `ALTER TABLE accounts ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0`; schema `4.json`
   exportado; caso no `VCCFinanceMigrationTest`. Backups antigos (sem a coluna) continuam a restaurar (DEFAULT 0).
2. `Account.isDefault`, mappers, `AccountDao.setDefault` (marca uma e limpa as restantes), `deactivate` limpa a marca.
3. `AccountRepository.setDefault/clearDefault`; `SaveAccountUseCase` aceita `isDefault`.
4. UI: switch "Conta por omissão" no editor; etiqueta "Por omissão" na lista de contas;
   `TransactionEditorViewModel` pré-seleciona a conta por omissão.
5. `TransferEditorRoute(withdrawal = true)` + modo levantamento no `TransferEditorViewModel/Screen`.
6. Testes unitários: `SaveAccountUseCaseTest`, `TransactionEditorViewModelTest`, `TransferEditorViewModelTest`.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| core/model/.../Account.kt | modify | campo `isDefault` |
| core/database/.../AccountEntity.kt, AccountDao.kt | modify | coluna e queries |
| core/database/.../VCCFinanceDatabase.kt, VCCFinanceMigrations.kt | modify | versão 4 e migração |
| core/database/schemas/.../4.json | create | schema exportado |
| core/data/.../AccountMapper.kt, AccountRepository.kt, LocalAccountRepository.kt | modify | mapeamento e repositório |
| core/data/.../SaveAccountUseCase.kt | modify | gravar conta por omissão |
| core/data/src/testFixtures/.../FakeRepositories.kt | modify | fake do repositório |
| feature/finance/.../accounts/* | modify | switch e etiqueta |
| feature/finance/.../transactions/* | modify | pré-seleção e modo levantamento |
| feature/finance/.../navigation/* | modify | rota e entrada no menu |
| feature/finance/src/main/res/values/strings.xml | modify | textos |
