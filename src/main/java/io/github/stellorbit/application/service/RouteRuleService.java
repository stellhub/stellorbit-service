package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.RouteRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RouteRuleRepository;
import io.github.stellorbit.interfaces.http.dto.CreateRouteRuleRequest;
import io.github.stellorbit.interfaces.http.dto.RouteRuleDetailRequest;
import io.github.stellorbit.interfaces.http.dto.RouteRuleDetailResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RouteRuleService
    extends RuleAggregateService<
        RouteRuleEntity, RouteRuleDetailRequest, CreateRouteRuleRequest, RouteRuleDetailResponse> {

  public RouteRuleService(
      GovernanceRuleRepository governanceRuleRepository, RouteRuleRepository routeRuleRepository) {
    super(governanceRuleRepository, routeRuleRepository, "ROUTE", "RouteRule");
  }

  @Override
  protected RouteRuleEntity toDetailEntity(UUID id, RouteRuleDetailRequest detail) {
    RouteRuleEntity entity = new RouteRuleEntity();
    entity.setId(id);
    entity.setRouteType(detail.routeType());
    entity.setTrafficDirection(defaultString(detail.trafficDirection(), "EAST_WEST"));
    entity.setProtocol(detail.protocol());
    entity.setGateways(defaultList(detail.gateways()));
    entity.setHosts(defaultList(detail.hosts()));
    entity.setSourceSelector(defaultMap(detail.sourceSelector()));
    entity.setMatchConditions(defaultList(detail.matchConditions()));
    entity.setDestinations(defaultList(detail.destinations()));
    entity.setRouteAction(defaultMap(detail.routeAction()));
    entity.setRewritePolicy(defaultMap(detail.rewritePolicy()));
    entity.setRedirectPolicy(defaultMap(detail.redirectPolicy()));
    entity.setMirrorPolicy(defaultMap(detail.mirrorPolicy()));
    entity.setFaultInjectionPolicy(defaultMap(detail.faultInjectionPolicy()));
    entity.setTimeoutPolicy(defaultMap(detail.timeoutPolicy()));
    entity.setRetryPolicy(defaultMap(detail.retryPolicy()));
    entity.setLoadBalancePolicy(defaultMap(detail.loadBalancePolicy()));
    entity.setLocalityPolicy(defaultMap(detail.localityPolicy()));
    return entity;
  }

  @Override
  protected RouteRuleDetailResponse toDetailResponse(RouteRuleEntity entity) {
    return new RouteRuleDetailResponse(
        entity.getId(),
        entity.getRouteType(),
        entity.getTrafficDirection(),
        entity.getProtocol(),
        entity.getGateways(),
        entity.getHosts(),
        entity.getSourceSelector(),
        entity.getMatchConditions(),
        entity.getDestinations(),
        entity.getRouteAction(),
        entity.getRewritePolicy(),
        entity.getRedirectPolicy(),
        entity.getMirrorPolicy(),
        entity.getFaultInjectionPolicy(),
        entity.getTimeoutPolicy(),
        entity.getRetryPolicy(),
        entity.getLoadBalancePolicy(),
        entity.getLocalityPolicy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
