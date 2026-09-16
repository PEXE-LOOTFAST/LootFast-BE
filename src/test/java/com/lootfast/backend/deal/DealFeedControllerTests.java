package com.lootfast.backend.deal;

import com.lootfast.backend.common.exception.GlobalExceptionHandler;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.controller.DealFeedController;
import com.lootfast.backend.deal.service.DealFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DealFeedControllerTests {

    private DealFeedService dealFeedService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dealFeedService = mock(DealFeedService.class);
        mockMvc = standaloneSetup(new DealFeedController(dealFeedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void feedUsesOneKilometerDefaultRadiusAndPaginationDefaults() throws Exception {
        when(dealFeedService.getFeed(10.776889, 106.700806, 1.0, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/deals")
                        .param("latitude", "10.776889")
                        .param("longitude", "106.700806"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(dealFeedService).getFeed(10.776889, 106.700806, 1.0, 0, 20);
    }
}
