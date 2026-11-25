package com.ichiyotsu.tokifonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shizuku UserService 胶水的纯逻辑部分：binder 路径 + tag 稳定性 + destroy 码。 */
class UserServiceGlueTest {

    @Test fun binderSourceIsProviderNotSui() {
        // Sui 要预装 Magisk 模块，未 root 的目标机根本没有；binder 只走 provider。
        assertEquals(UserServiceGlue.BinderSource.PROVIDER, UserServiceGlue.chooseBinderSource())
    }

    @Test fun userServiceTagIsExplicitAndStable() {
        // tag 必须显式设、不能是类名：R8 后类名不稳定，默认 tag（类名）会对不上。
        val tag = UserServiceGlue.USER_SERVICE_TAG
        assertTrue(tag.isNotBlank())
        assertNotEquals(TokiFontsRootService::class.java.name, tag)
        assertEquals("com.ichiyotsu.tokifontspro.root", tag)
    }

    @Test fun userServiceVersionStartsAtOne() {
        assertEquals(1, UserServiceGlue.USER_SERVICE_VERSION)
    }

    @Test fun destroyTransactionMatchesUpstreamConstant() {
        // 16777115：ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy。
        // service 侧 onTransact 拦截这个码后自调 System.exit()，进程才会被杀。
        assertEquals(16777115, TokiFontsRootService.TRANSACTION_DESTROY)
    }
}
