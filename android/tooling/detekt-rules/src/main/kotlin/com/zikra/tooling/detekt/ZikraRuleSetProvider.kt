package com.zikra.tooling.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class ZikraRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "zikra"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(HardcodedComposeText(config)),
    )
}
