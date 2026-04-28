package org.jackfruit.keliri.repository;

import org.jackfruit.keliri.model.PaymentTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, String> {
    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    List<PaymentTransaction> findByAdminId(String adminId);
}
