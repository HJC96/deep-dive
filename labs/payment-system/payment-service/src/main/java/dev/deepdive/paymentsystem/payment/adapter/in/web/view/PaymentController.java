package dev.deepdive.paymentsystem.payment.adapter.in.web.view;

import dev.deepdive.paymentsystem.common.WebAdapter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@WebAdapter
@Controller
public class PaymentController {

    @GetMapping("/success")
    public String success() {
        return "success";
    }
    @GetMapping("/fail")
    public String fail() {
        return "fail";
    }
}
