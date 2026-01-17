package io.readingrecord.order.application.service

import io.readingrecord.order.application.exception.OrderNotFoundException
import io.readingrecord.order.application.exception.OrderUpdateFailedException
import io.readingrecord.order.application.port.`in`.OrderUseCase
import io.readingrecord.order.application.port.out.OrderEventPublisher
import io.readingrecord.order.application.port.out.OrderPersistencePort
import io.readingrecord.order.domain.command.PlaceOrderCommand
import io.readingrecord.order.domain.command.UpdateOrderStatusCommand
import io.readingrecord.order.domain.model.Order
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 서비스 구현체
 *
 * Clean Architecture의 Application Layer에 위치하며,
 * 주문 생성, 조회, 상태 변경 유스케이스를 처리합니다.
 */
@Service
@Transactional
class OrderService(
    private val orderPersistencePort: OrderPersistencePort,
    private val orderEventPublisher: OrderEventPublisher
) : OrderUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문 생성
     *
     * @param command 주문 생성 커맨드
     * @return 생성된 주문
     */
    override fun placeOrder(command: PlaceOrderCommand): Order {
        logger.info("Placing order - customerId: {}, productId: {}, quantity: {}",
            command.customerId, command.productId, command.quantity)

        val order = command.toPlaceOrder()
        val savedOrder = orderPersistencePort.save(order)

        logger.info("Order placed successfully - orderId: {}", savedOrder.id)

        val orderPlacedEvent = savedOrder.toOrderPlacedEvent()
        orderEventPublisher.publishOrderPlacedEvent(orderPlacedEvent)

        logger.info("OrderPlaced event published - orderId: {}", savedOrder.id)

        return savedOrder
    }

    /**
     * 주문 조회
     *
     * @param orderId 주문 ID
     * @return 조회된 주문
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    override fun getOrder(orderId: Long): Order {
        logger.debug("Fetching order - orderId: {}", orderId)

        return orderPersistencePort.findById(orderId)
            ?: throw OrderNotFoundException("주문을 찾을 수 없습니다. orderId: $orderId")
    }

    /**
     * 주문 상태 변경
     *
     * @param command 상태 변경 커맨드
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     * @throws OrderUpdateFailedException 상태 변경에 실패한 경우
     */
    override fun updateOrderStatus(command: UpdateOrderStatusCommand) {
        logger.info("Updating order status - orderId: {}, newStatus: {}",
            command.orderId, command.newStatus)

        val existingOrder = orderPersistencePort.findById(command.orderId)
            ?: throw OrderNotFoundException("주문을 찾을 수 없습니다. orderId: ${command.orderId}")

        val success = orderPersistencePort.updateStatus(command.orderId, command.newStatus)

        if (!success) {
            logger.error("Failed to update order status - orderId: {}", command.orderId)
            throw OrderUpdateFailedException("주문 상태 업데이트에 실패했습니다. orderId: ${command.orderId}")
        }

        logger.info("Order status updated successfully - orderId: {}, oldStatus: {}, newStatus: {}",
            command.orderId, existingOrder.status, command.newStatus)

        //TODO 주문 상태 변경 이벤트 발행
    }
}
