package com.example.zuora.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.example.zuora.client.ZuoraClient;

public class BillRunService {

    private final ZuoraClient zuoraClient;
    private final LambdaLogger logger;

    public BillRunService(ZuoraClient zuoraClient, LambdaLogger logger) {
        this.zuoraClient = zuoraClient;
        this.logger = logger;
    }

    /**
     * 指定アカウントの Bill Run を作成して再請求をトリガーする。
     *
     * @return 作成された Bill Run の ID
     */
    public String createBillRun(String accountId, String date) throws Exception {
        logger.log("Creating bill run: accountId=" + accountId + ", date=" + date);
        String billRunId = zuoraClient.createBillRun(accountId, date);
        logger.log("Bill run created: billRunId=" + billRunId);
        return billRunId;
    }
}
