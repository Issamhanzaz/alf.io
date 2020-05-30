package alfio.manager.payment;
import alfio.controller.api.v2.MultiSafepay.classes.Order;
import alfio.controller.api.v2.MultiSafepay.classes.PaymentOptions;
import alfio.controller.api.v2.MultiSafepay.client.MultiSafepayClient;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.transaction.*;
import alfio.repository.TransactionRepository;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import java.util.*;
import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
@AllArgsConstructor
public class MultisafepayManager implements PaymentProvider {
    private final ConfigurationManager configurationManager;
    private final TransactionRepository transactionRepository;

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        return EnumSet.of(PaymentMethod.IDEAL);
    }


    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.MULTISAFEPAY;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return paymentMethod == PaymentMethod.IDEAL && isActive(context);
    }

    @Override
    public boolean accept(Transaction transaction)  {
        return PaymentProxy.MULTISAFEPAY == transaction.getPaymentProxy();
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return PaymentMethod.IDEAL;
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        return configurationManager.getFor(MULTISAFEPAY_ENABLED, paymentContext.getConfigurationLevel()).getValueAsBooleanOrDefault(true)
            && (paymentContext.getConfigurationLevel().getPathLevel() != ConfigurationPathLevel.EVENT || !paymentContext.getEvent().isOnline());
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {

        MultiSafepayClient.init(
            configurationManager.getFor(Set.of(MOLLIE_CONNECT_LIVE_MODE), ConfigurationLevel.organization(spec.getEvent()
                .getOrganizationId())).get(MOLLIE_CONNECT_LIVE_MODE)
                .getValueAsBooleanOrDefault(true),
            configurationManager.getFor(Set.of(MOLLIE_API_KEY), ConfigurationLevel.organization(spec.getEvent()
                .getOrganizationId()))
                .get(MOLLIE_API_KEY)
                .getRequiredValue());

        Order order = new Order();
        String redirectUrl = "https://boegafun.hsbusiness.nl/event/"+ spec.getEvent().getDisplayName()+ "/reservation/"
            + spec.getReservationId()+"/success";

        order.setRedirect(
            spec.getReservationId(),
            spec.getEvent().getDisplayName(),
            spec.getPriceWithVAT(),
            spec.getCurrencyCode(),
            new PaymentOptions(
                redirectUrl,
                redirectUrl,
                redirectUrl
            )
        );

        JsonObject jsonResponse = MultiSafepayClient.createOrder(order);
        String paymentUrl = MultiSafepayClient.getPaymenUrl(jsonResponse);
        PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository, getPaymentProxy());
        return PaymentResult.redirect(paymentUrl);
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        return null;
    }

    @Override
    public PaymentResult getTokenAndPay(PaymentSpecification spec) {
        return doPayment(spec);
    }

//    @Override
//    public Map<String, ?> getModelOptions(PaymentContext context) {
//        Map<String, Object> model = new HashMap<>();
//        boolean recaptchaEnabled = configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(context.getConfigurationLevel());
//        model.put("captchaRequestedForOffline", recaptchaEnabled);
//        if(recaptchaEnabled) {
//            model.put("recaptchaApiKey", configurationManager.getForSystem(RECAPTCHA_API_KEY).getValue().orElse(null));
//        }
//        return model;
//    }

//    private PaymentResult getPaymentResult(PaymentSpecification spec) {
//        try {
//            var event = spec.getEvent();
//            var configuration = getConfiguration(ConfigurationLevel.event(event));
//            var reservationId = spec.getReservationId();
//            var reservation = ticketReservationRepository.findReservationById(reservationId);
//            String baseUrl = StringUtils.removeEnd(configuration.get(BASE_URL).getRequiredValue(), "/");
//
//            var existingTransaction = transactionRepository.loadOptionalByReservationId(reservationId)
//                .filter(t -> t.getPaymentProxy() == PaymentProxy.MULTISAFEPAY && StringUtils.isNotEmpty(t.getPaymentId()));
//
//            if(existingTransaction.isEmpty()) {
//                return doPayment(spec);
//            } else {
//                return tryToReuseExistingTransaction(existingTransaction.get(), reservation, spec, baseUrl, configuration);
//            }
//        } catch (Exception e) {
//            log.warn(e);
//            return PaymentResult.failed(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
//        }
//    }

//    private Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> getConfiguration(ConfigurationLevel configurationLevel) {
//        return configurationManager.getFor(ALL_OPTIONS, configurationLevel);
//    }


}
