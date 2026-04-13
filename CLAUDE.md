# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
# ビルド（コンパイル + テスト）。gradlew は ZuoraCreditAndRebillFunction/ 内にある
export JAVA_HOME=/home/buri17/.vscode/extensions/redhat.java-1.53.0-linux-x64/jre/21.0.10-linux-x86_64
cd ZuoraCreditAndRebillFunction && ./gradlew build

# テストのみ
cd ZuoraCreditAndRebillFunction && ./gradlew test

# 単一テストクラス
cd ZuoraCreditAndRebillFunction && ./gradlew test --tests "com.example.zuora.service.InvoiceCorrectionServiceTest"

# Lambda デプロイ用 fat JAR（sam build でも自動実行される）
cd ZuoraCreditAndRebillFunction && ./gradlew shadowJar  # build/libs/zuora-credit-and-rebill-1.0.0.jar
```

`gradlew` が存在しない場合は `ZuoraCreditAndRebillFunction/` 内で `gradle wrapper` を実行（Java が必要）。

## AWS SAM デプロイ

```bash
# 初回のみ: SSM Parameter Store に認証情報を登録
aws ssm put-parameter --name /zuora-credit-and-rebill/client-id \
  --value "<CLIENT_ID>" --type String
aws ssm put-parameter --name /zuora-credit-and-rebill/client-secret \
  --value "<CLIENT_SECRET>" --type SecureString

# ビルド（BuildMethod: gradle で SAM が ./gradlew build を自動実行）
export JAVA_HOME=/home/buri17/.vscode/extensions/redhat.java-1.53.0-linux-x64/jre/21.0.10-linux-x86_64
sam build

# デプロイ（初回は guided、以降はそのまま）
sam deploy --guided   # 初回
sam deploy            # 2回目以降

# 本番環境へのデプロイ
sam deploy --parameter-overrides ZuoraEnv=PROD
```

## アーキテクチャ

### 処理の流れ

```
API Gateway POST
  → OrderEventHandler (Lambda ハンドラ)
  → InvoiceCorrectionService.correct()
      1. ZuoraClient.getOrderTriggerDates(orderNumber)
             ordersApi → subscriptions[] → orderActions[] → triggerDates[]
      2. ZuoraClient.getPostedInvoices(accountId)
             billingDocumentsApi (status=POSTED, type=INVOICE)
             + invoicesApi.getInvoiceItems() でサービス期間を取得（min/max 集約）
      3. triggerDate がサービス期間に含まれる請求書をフィルタ
      4. ZuoraClient.reverseInvoice() × n
      5. ZuoraClient.createBillRun()
```

### レイヤー構造

- **handler**: Lambda エントリーポイント。Jackson で `OrderEvent` をパースし、サービスを呼ぶ
- **service**: ビジネスロジック。`ZuoraClient` に依存（テストでモック可能）
- **client**: `ZuoraClient` は公式 SDK (`com.zuora.ZuoraClient`) の薄いラッパー。SDK の型を内部で使い、サービス層には `Invoice` ドメインモデルを返す
- **model**: `OrderEvent`（受信ペイロード）と `Invoice`（ドメインモデル）。SDK の型はクライアント層に閉じている

### 重要な設計判断

- **フィルタ基準**: `OrderDate` ではなく Order Action の `triggerDate` を使用。1 オーダーに複数 Action があるため全 triggerDate の union で判定
- **サービス期間**: `GET /v1/billing-documents` にはサービス日付がないため、各請求書の Invoice Item から `serviceStartDate`/`serviceEndDate` を取得して集約（N+1 クエリ）
- **Bill Run のアカウント指定**: `BillRunFilter.filterType(ACCOUNT).accountId()` を使用（旧 CRUD API の `AccountId` フィールドではない）
- **日付型**: SDK の日付フィールドはすべて `LocalDate`（String ではない）

## 環境変数

| 変数 | 説明 |
|------|------|
| `ZUORA_CLIENT_ID` | OAuth 2.0 クライアント ID |
| `ZUORA_CLIENT_SECRET` | OAuth 2.0 クライアントシークレット |
| `ZUORA_ENV` | `SBX` / `PROD` / `SBX_NA` / `SBX_EU` / `PROD_NA` / `PROD_EU` / `PROD_AP` |

## Zuora SDK

`com.zuora.sdk:zuora-sdk-java:3.16.0`。OAuth トークン管理は SDK が自動処理。

主要 API アクセスパターン:
```java
sdk.ordersApi().getOrderApi(orderNumber).execute()
sdk.billingDocumentsApi().getBillingDocumentsApi().accountId(id).status(POSTED).execute()
sdk.invoicesApi().getInvoiceItemsApi(invoiceId).execute()
sdk.invoicesApi().reverseInvoiceApi(invoiceId, request).execute()
sdk.billRunApi().createBillRunApi(request).execute()
```

`ZuoraEnv` は `com.zuora.ZuoraClient` の static inner enum。

## Lambda ハンドラ設定

```
com.example.zuora.handler.OrderEventHandler::handleRequest
```
