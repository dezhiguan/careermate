package com.careermate.opportunity.service;

import com.careermate.common.api.PageResult;
import com.careermate.opportunity.dto.OpportunityCitiesVO;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;

public interface OpportunityService {

    PageResult<OpportunityListItemVO> list(Long userId, OpportunityListRequest request);

    PageResult<OpportunityListItemVO> listCached(Long userId, OpportunityListRequest request);

    /** 机会页城市筛选选项：可选城市列表 + 默认选中城市（取自用户画像目标城市）。 */
    OpportunityCitiesVO cities(Long userId);

    OpportunityDetailVO detail(Long userId, String jdId);

    OpportunityPrepareResponse prepare(Long userId, String jdId);
}
