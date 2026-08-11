package com.careermate.market.service;

import com.careermate.common.catalog.CityCatalog;
import com.careermate.market.dto.MarketDimensionsVO;
import com.careermate.market.support.MarketDefaults;
import com.careermate.market.support.MarketExperience;
import com.careermate.market.support.MarketRoleCatalog;
import org.springframework.stereotype.Service;

/**
 * 下发行情查询维度字典。前端不再各自硬编码岗位/城市/经验清单。
 */
@Service
public class MarketDimensionService {

    private final MarketRoleCatalog roleCatalog;
    private final CityCatalog cityCatalog;

    public MarketDimensionService(MarketRoleCatalog roleCatalog, CityCatalog cityCatalog) {
        this.roleCatalog = roleCatalog;
        this.cityCatalog = cityCatalog;
    }

    public MarketDimensionsVO dimensions() {
        return new MarketDimensionsVO(
                roleCatalog.groups(),
                cityCatalog.cities(),
                MarketExperience.options(),
                MarketDefaults.ROLE,
                MarketDefaults.CITY,
                MarketDefaults.YEARS
        );
    }
}
