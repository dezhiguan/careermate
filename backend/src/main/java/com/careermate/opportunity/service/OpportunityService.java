package com.careermate.opportunity.service;

import com.careermate.common.api.PageResult;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;

public interface OpportunityService {

    PageResult<OpportunityListItemVO> list(Long userId, OpportunityListRequest request);

    OpportunityDetailVO detail(Long userId, String jdId);

    OpportunityPrepareResponse prepare(Long userId, String jdId);
}
