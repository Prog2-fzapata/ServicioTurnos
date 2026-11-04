package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.validation;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneNumberConstraintValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    // Un valor ausente lo rechaza @NotBlank, con su propio mensaje
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PhoneNumber.isValid(value);
    }
}
