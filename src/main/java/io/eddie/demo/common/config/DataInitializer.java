package io.eddie.demo.common.config;

import io.eddie.demo.domain.accounts.model.dto.CreateAccountRequest;
import io.eddie.demo.domain.accounts.model.entity.Account;
import io.eddie.demo.domain.accounts.service.AccountService;
import io.eddie.demo.domain.carts.model.entity.Cart;
import io.eddie.demo.domain.carts.model.entity.CartItem;
import io.eddie.demo.domain.carts.model.vo.CreateCartItemRequest;
import io.eddie.demo.domain.carts.service.CartService;
import io.eddie.demo.domain.deposits.model.entity.Deposit;
import io.eddie.demo.domain.deposits.model.entity.DepositHistory;
import io.eddie.demo.domain.deposits.service.DepositService;
import io.eddie.demo.domain.orders.model.entity.Orders;
import io.eddie.demo.domain.orders.model.vo.CreateOrderRequest;
import io.eddie.demo.domain.orders.service.OrderService;
import io.eddie.demo.domain.payments.model.dto.PaymentRequest;
import io.eddie.demo.domain.payments.model.vo.PaymentType;
import io.eddie.demo.domain.payments.service.PaymentService;
import io.eddie.demo.domain.products.model.dto.UpsertProductRequest;
import io.eddie.demo.domain.products.model.entity.Product;
import io.eddie.demo.domain.products.service.ProductService;
import io.eddie.demo.domain.settlements.model.entity.Settlement;
import io.eddie.demo.domain.settlements.model.vo.SettlementStatus;
import io.eddie.demo.domain.settlements.repository.SettlementRepository;
import io.eddie.demo.domain.settlements.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AccountService accountService;
    private final ProductService productService;
    private final CartService cartService;

    private final DepositService depositService;

    private final OrderService orderService;

//    private final SettlementService settlementService;
    private final PaymentService paymentService;
    
    private final SettlementRepository settlementRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        // 장바구니 목록
        List<Cart> cartList = new ArrayList<>();

        // 회원 목록
        List<Account> accountList = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> accountService.create(new CreateAccountRequest("user%d".formatted(i), "user%d".formatted(i), "user%d@user.com".formatted(i))))
                .toList();

        // 상품 목록
        List<Product> productList = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> productService.save(
                        accountList.get(i % accountList.size()).getCode()
                        , new UpsertProductRequest("product%d".formatted(i), "product%d description".formatted(i), randomPrice())
                ))
                .toList();

        // 1번 회원
        Account targetAccount = accountList.getFirst();

        // 임시 상품
        Product tempProduct = productService.save(targetAccount.getCode(),
                new UpsertProductRequest("주문제작 상품", "이것은 테스트를 위한 상품입니다.", 10L));

        // 상품 생성 요청
        CreateCartItemRequest createCartItemRequest = new CreateCartItemRequest(
                tempProduct.getCode(),
                tempProduct.getName(),
                tempProduct.getPrice(),
                3
        );

        // 장바구니 항목
        CartItem targetItem = cartService.appendItem(targetAccount.getCode(), createCartItemRequest);

        // 오류 시나리오
//        Account otherAccount = accountList.getLast();
//        CartItem error = cartService.getItem(otherAccount.getCode(), cartList.getFirst().getCode());

        // 장바구니 항목 증감
        CartItem increased = cartService.increaseQuantity(targetAccount.getCode(), targetItem.getCode());
        log.info("%%increased.getQuantity() = {}", increased.getQuantity());

        CartItem decreased = cartService.decreaseQuantity(targetAccount.getCode(), targetItem.getCode());
        log.info("%%decreased.getQuantity() = {}", decreased.getQuantity());

        // 장바구니 아이템 삭제
//        cartService.deleteItem(targetAccount.getCode(), targetItem.getCode());

        // 특정 회원 입금
//        depositService.charge(
//                targetAccount.getCode(),
//                DepositHistory.DepositHistoryType.CHARGE_TRANSFER,
//                100_000_000L
//        );

        // 모든 회원 입금
        accountList.forEach(a ->
                depositService.charge(a.getCode()
                        , DepositHistory.DepositHistoryType.CHARGE_TRANSFER
                        , 100_000_000L)
        );

        // 1번 회원 예치금
        Deposit targetDeposit1 = depositService.getByAccountCode(targetAccount.getCode());
        log.info("targetDeposit1.getBalance() = {}", targetDeposit1.getBalance());

        // 1번 회원 예치금 출금
        depositService.withdraw(
                targetAccount.getCode(),
                DepositHistory.DepositHistoryType.PAYMENT_TOSS,
                30_000L
        );

        // 1번 회원 예치금
        Deposit targetDeposit2 = depositService.getByAccountCode(targetAccount.getCode());
        log.info("targetDeposit2.getBalance() = {}", targetDeposit2.getBalance());

        // 모든 회원 장바구니에 아이템 추가
        Map<String, Orders> orderList = new HashMap<>();
        Map<String, List<String>> accountToCartItemCodes = new HashMap<>();

        IntStream.rangeClosed(1, 20)
                .forEach(i -> {
                    Product targetProduct = productList.get(i % productList.size());

                    CreateCartItemRequest cartItemReq = new CreateCartItemRequest(
                            targetProduct.getCode(),
                            targetProduct.getName(),
                            targetProduct.getPrice(),
                            randomQuantity()
                    );

                    // 모든 회원에게 아이템 추가
                    accountList.forEach(a -> {
                        CartItem created = cartService.appendItem(a.getCode(), cartItemReq);
                        accountToCartItemCodes
                                .computeIfAbsent(a.getCode(), k -> new ArrayList<>())
                                .add(created.getCode());
                    });
                });

        // 장바구니 기준으로 주문 생성 (계정별로 수집한 CartItem 코드 전달)
        accountList.forEach(a -> {
            List<String> itemCodes = accountToCartItemCodes.getOrDefault(a.getCode(), List.of());
            if (!itemCodes.isEmpty()) {
                Orders order = orderService.createOrder(a.getCode(), new CreateOrderRequest(itemCodes));
                orderList.put(a.getCode(), order);
            }
        });

        orderList.forEach((a, o) -> {
            paymentService.pay(new PaymentRequest(a, PaymentType.DEPOSIT, o.getCode(), o.getTotalPrice()));
        });

        /*
        정산 부분 주석처리
        String startDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));


        // 정산 준비
        settlementService.prepare(startDate);

        // h2-console 확인

        // 정산 실행
        settlementService.process(startDate);
        */

        List<Settlement> settlements = IntStream.rangeClosed(1, 50_000)
                .mapToObj(i -> {

                    Product targetProduct = productList.get(i % productList.size());

                    Settlement settlement = Settlement.builder()
                            .buyerCode(accountList.get(i % accountList.size()).getCode())
                            .sellerCode(targetProduct.getAccountCode())
                            .settlementRate(0.5)
                            .totalAmount(targetProduct.getPrice())
                            .build();

                    settlement.applySettlementPolicy();

                    return settlement;
                })
                .toList();

        settlementRepository.saveAll(settlements);

        log.info("DONE!");

    }

    private Long randomPrice() {
        return Math.abs(new Random().nextLong() % 1_000);
    }

    private int randomQuantity() {
        return (int) ((Math.random() + 1) * 10);
    }

}
