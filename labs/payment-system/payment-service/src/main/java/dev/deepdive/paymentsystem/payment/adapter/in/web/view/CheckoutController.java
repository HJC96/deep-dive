package dev.deepdive.paymentsystem.payment.adapter.in.web.view;

import dev.deepdive.paymentsystem.common.WebAdapter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@WebAdapter
@Controller
public class CheckoutController {

    @GetMapping("/checkout")
    public String checkout() {
        return "checkout";
    }
}
