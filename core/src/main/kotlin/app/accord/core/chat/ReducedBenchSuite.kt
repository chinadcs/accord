package app.accord.core.chat

/** Explicit corpus/profile pairing shared by the Android bench and deterministic tests. */
object ReducedBenchSuite {
    data class Selection(val cases:List<ReducedBattery.Case>,val sha256:String,val batteryVersion:Int,val validatorVersion:Int)
    fun select(suite:String,profileVersion:Int,singleCall:Boolean=false,control:Boolean=false):Selection {
        if (suite == "financial-contract-v1") {
            require(profileVersion == 9 && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256, 1, 12)
        }
        if (suite == "financial-contract-v2") {
            require(profileVersion == FinancialContractRegression.V2_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V2_VERSION, FinancialContractRegression.V2_VALIDATOR)
        }
        if (suite == "financial-contract-v3") {
            require(profileVersion == FinancialContractRegression.PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.VERSION, FinancialContractRegression.VALIDATOR)
        }
        if (suite == "financial-contract-v4") {
            require(profileVersion == FinancialContractRegression.V4_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V4_VERSION, FinancialContractRegression.V4_VALIDATOR)
        }
        if (suite == "financial-contract-v5") {
            require(profileVersion == FinancialContractRegression.V5_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V5_VERSION, FinancialContractRegression.V5_VALIDATOR)
        }
        if (suite == "financial-contract-v6") {
            require(profileVersion == FinancialContractRegression.V6_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V6_VERSION, FinancialContractRegression.V6_VALIDATOR)
        }
        if (suite == "financial-contract-v7") {
            require(profileVersion == FinancialContractRegression.V7_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V7_VERSION, FinancialContractRegression.V7_VALIDATOR)
        }
        if (suite == "financial-contract-v8") {
            require(profileVersion == FinancialContractRegression.V8_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V8_VERSION, FinancialContractRegression.V8_VALIDATOR)
        }
        if (suite == "financial-contract-v9") {
            require(profileVersion == FinancialContractRegression.V9_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V9_VERSION, FinancialContractRegression.V9_VALIDATOR)
        }
        if (suite == "financial-contract-v10") {
            require(profileVersion == FinancialContractRegression.V10_PROFILE && !singleCall)
            return Selection(FinancialContractRegression.cases, FinancialContractRegression.sha256,
                FinancialContractRegression.V10_VERSION, FinancialContractRegression.V10_VALIDATOR)
        }
        if (suite == "independent-financial-v2") {
            require(profileVersion == FinancialContractRegression.V5_PROFILE && !singleCall)
            return Selection(IndependentFinancialBatteryV2.cases, IndependentFinancialBatteryV2.sha256,
                IndependentFinancialBatteryV2.VERSION, FinancialContractRegression.V5_VALIDATOR)
        }
        if (suite == "independent-financial-v1") {
            require(profileVersion == FinancialContractRegression.PROFILE && !singleCall)
            return Selection(IndependentFinancialBattery.cases, IndependentFinancialBattery.sha256,
                IndependentFinancialBattery.VERSION, FinancialContractRegression.VALIDATOR)
        }
        if(suite=="qualification-v5") {
            require(profileVersion==7 && !singleCall) { "Qualification v5 requires profile 7 without the historical single-action variant" }
            check(ProtectedQualificationBattery.sha256=="4c8b39ade6d9e2c86d0db2acc8beae48ee7a8d62e530247e3d8e387cc0978eea")
            return Selection(ProtectedQualificationBattery.cases,ProtectedQualificationBattery.sha256,5,9)
        }
        if(suite=="selector-v1") {
            require(profileVersion==7 && !singleCall) { "Selector v1 requires profile 7 without the historical single-action variant" }
            return Selection(SelectorComparisonBattery.cases,SelectorComparisonBattery.sha256,SelectorComparisonBattery.VERSION,9)
        }
        require(suite in listOf("screen","full","additional") && !control)
        require(profileVersion in 1..4 && !(singleCall && profileVersion==1))
        val cases=when(suite){"screen"->ReducedBattery.screen;"additional"->ReducedAdditionalBattery.cases;else->ReducedBattery.cases}
        return Selection(cases,if(suite=="additional")ReducedAdditionalBattery.sha256 else ReducedBattery.sha256,
            if(suite=="additional")ReducedAdditionalBattery.VERSION else ReducedBattery.VERSION,
            if(profileVersion==4)5 else 4)
    }
}
