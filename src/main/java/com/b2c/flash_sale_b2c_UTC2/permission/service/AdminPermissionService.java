package com.b2c.flash_sale_b2c_UTC2.permission.service;

import com.b2c.flash_sale_b2c_UTC2.permission.dto.AclPermissionResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.FeatureFlagResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.InternalRoleGroupResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ModerateReviewRequest;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ReportedReviewResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.SystemRoleResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ToggleFeatureFlagRequest;

import java.util.List;

public interface AdminPermissionService {

    List<SystemRoleResponse> getSystemRoles();

    List<InternalRoleGroupResponse> getInternalRoleGroups();

    List<AclPermissionResponse> getAclPermissions();

    List<FeatureFlagResponse> getFeatureFlags();

    FeatureFlagResponse toggleFeatureFlag(String code, ToggleFeatureFlagRequest request);

    List<ReportedReviewResponse> getReportedReviews();

    ReportedReviewResponse moderateReview(Long reviewId, ModerateReviewRequest request);
}
