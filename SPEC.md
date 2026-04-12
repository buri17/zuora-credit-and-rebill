# zuora-credit-and-rebill

## 概要

Zuora の `Order.Completed` イベントを受け取り、請求済み期間に影響するオーダーを補正するツール。
影響を受けるすべての請求書を Invoice Reversal で取り消し（Credit Memo 自動生成）、Bill Run で再請求する。

## 処理フロー

```
API Gateway (POST)
  → OrderEventHandler (Lambda)
  → OrderEvent パース（orderId, orderNumber, accountId 取得）
  → ZuoraClient: Order API でオーダーを取得
      → 全 Order Action の triggerDate 一覧を収集（重複除去）
  → triggerDate が空の場合はスキップ
  → ZuoraClient: アカウントの Posted 請求書一覧取得（Invoice Item からサービス期間を集約）
  → いずれかの triggerDate がサービス期間に含まれる請求書をフィルタ
  → 各請求書に対して Invoice Reversal API 呼び出し
      → Credit Memo 自動生成 + 元請求書キャンセル
  → Bill Run 作成 → 再請求
```

## Zuora callout 設定

`Order.Completed` callout に以下のフィールドを含めること。

| callout フィールド | JSON キー | 説明 |
|---|---|---|
| `Order.Id` | `Id` | オーダー ID |
| `Order.OrderNumber` | `OrderNumber` | オーダー番号（Order API の呼び出しに使用） |
| `Order.AccountId` | `AccountId` | アカウント ID |

## ファイル構成

```
zuora-credit-and-rebill/
├── build.gradle
├── settings.gradle
├── gradle/wrapper/
├── .env.example
├── SPEC.md
└── src/
    ├── main/java/com/example/zuora/
    │   ├── handler/
    │   │   └── OrderEventHandler.java         # Lambda エントリーポイント
    │   ├── service/
    │   │   ├── InvoiceCorrectionService.java  # メインロジック
    │   │   └── BillRunService.java            # Bill Run 作成
    │   ├── client/
    │   │   └── ZuoraClient.java              # Zuora SDK ラッパー
    │   ├── model/
    │   │   ├── OrderEvent.java               # Order.Completed ペイロード
    │   │   └── Invoice.java                  # 請求書ドメインモデル
    │   └── config/
    │       └── ZuoraConfig.java              # 環境変数設定
    └── test/java/com/example/zuora/
        └── service/
            └── InvoiceCorrectionServiceTest.java
```

## 環境変数

| 変数名 | 説明 |
|--------|------|
| `ZUORA_CLIENT_ID` | OAuth 2.0 クライアント ID |
| `ZUORA_CLIENT_SECRET` | OAuth 2.0 クライアントシークレット |
| `ZUORA_ENV` | 接続先環境（`SBX` / `PROD` / `SBX_NA` / `SBX_EU` / `PROD_NA` / `PROD_EU` / `PROD_AP`） |

## 使用 Zuora API（SDK 経由）

| 操作 | SDK メソッド |
|------|-------------|
| オーダー取得 | `ordersApi().getOrderApi(orderNumber).execute()` |
| 請求書一覧取得 | `billingDocumentsApi().getBillingDocumentsApi().accountId().status(POSTED).execute()` |
| Invoice Item 取得 | `invoicesApi().getInvoiceItemsApi(invoiceId).execute()` |
| Invoice Reversal | `invoicesApi().reverseInvoiceApi(invoiceId, request).execute()` |
| Bill Run 作成 | `billRunApi().createBillRunApi(request).execute()` |

## ビルド・デプロイ

```bash
# fat JAR ビルド（Lambda デプロイ用）
./gradlew shadowJar

# テスト実行
./gradlew test
```

Lambda ハンドラ: `com.example.zuora.handler.OrderEventHandler::handleRequest`

## 設計上の判断

- **triggerDate ベースのフィルタ**: `OrderDate` ではなく Order Action の `triggerDate` を使用。
  1 つのオーダーに複数 Order Action が存在するため、全 Action の triggerDate を収集し、
  いずれかがサービス期間に含まれる請求書を補正対象とする。
- **Invoice Reversal** を利用することで Credit Memo の手動生成が不要。
- **サービス期間の集約**: `GET /v1/billing-documents` にはサービス日付が含まれないため、
  各請求書の Invoice Item から serviceStartDate / serviceEndDate を取得し min/max で集約する。
- **SDK 認証**: `com.zuora.sdk:zuora-sdk-java` が OAuth トークン取得・更新を自動管理。
