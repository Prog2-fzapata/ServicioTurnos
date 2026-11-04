package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// Teléfono con la regla de la cátedra (PhoneNumber): se admiten espacios, guiones, paréntesis y puntos
@Documented
@Constraint(validatedBy = PhoneNumberConstraintValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhoneNumber {

    String message() default "El teléfono no es válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
