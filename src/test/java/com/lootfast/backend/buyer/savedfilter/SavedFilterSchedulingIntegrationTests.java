package com.lootfast.backend.buyer.savedfilter;

import static org.assertj.core.api.Assertions.assertThat;

import com.lootfast.backend.buyer.savedfilter.notification.SavedFilterNotificationScheduler;
import com.lootfast.backend.buyer.savedfilter.notification.SavedFilterNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "lootfast.saved-filter-notifications.enabled=true")
class SavedFilterSchedulingIntegrationTests {
  @Autowired ApplicationContext context;

  // Keep the scheduler enabled while preventing background work from contacting services.
  @MockitoBean SavedFilterNotificationService notificationService;

  @Test
  void enabledNotificationsStartAndRegisterTheirDedicatedScheduler() {
    assertThat(context.getBean("savedFilterNotificationScheduler"))
        .isInstanceOf(SavedFilterNotificationScheduler.class);
    var scheduler = context.getBean(
        "savedFilterNotificationTaskScheduler", ThreadPoolTaskScheduler.class);
    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
    assertThat(scheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);
    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
        .isNotEmpty();
  }
}
