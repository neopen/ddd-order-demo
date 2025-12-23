package com.hengline.ddd.domain.event;

import java.io.Serializable;

/**
 * 领域事件接口
 */
public interface DomainEvent extends Serializable {

    String getEventId();

}