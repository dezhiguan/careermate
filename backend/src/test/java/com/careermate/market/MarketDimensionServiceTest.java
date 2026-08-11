package com.careermate.market;

import com.careermate.common.catalog.CityCatalog;
import com.careermate.market.dto.MarketDimensionsVO;
import com.careermate.market.dto.MarketRoleGroupVO;
import com.careermate.market.service.MarketDimensionService;
import com.careermate.market.support.MarketRoleCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDimensionServiceTest {

    private final MarketDimensionService service =
            new MarketDimensionService(new MarketRoleCatalog(), new CityCatalog());

    @Test
    void defaultsAreJavaBackendGuangzhouAnyExperience() {
        MarketDimensionsVO vo = service.dimensions();

        assertEquals("Java后端", vo.defaultRole());
        assertEquals("广州", vo.defaultCity());
        assertEquals("不限", vo.defaultYears());
    }

    @Test
    void aiRolesAreExposedAsTheirOwnGroup() {
        List<MarketRoleGroupVO> groups = service.dimensions().roleGroups();

        MarketRoleGroupVO ai = groups.stream()
                .filter(g -> g.group().contains("AI"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("岗位目录必须包含 AI 相关分组"));

        assertTrue(ai.roles().contains("AI应用工程师"));
        assertTrue(ai.roles().contains("大模型算法工程师"));
        assertTrue(ai.roles().contains("AI Agent工程师"));
        assertTrue(ai.roles().contains("RAG工程师"));
    }

    @Test
    void roleCatalogHasNoDuplicatesAcrossGroups() {
        List<String> flat = new MarketRoleCatalog().roles();

        assertEquals(flat.size(), flat.stream().distinct().count(), "岗位清单不得有重复项");
    }

    @Test
    void cityAndYearsListsLeadWithAny() {
        MarketDimensionsVO vo = service.dimensions();

        assertEquals("不限", vo.cities().get(0));
        assertEquals("不限", vo.years().get(0));
        assertTrue(vo.cities().contains("广州"));
    }
}
