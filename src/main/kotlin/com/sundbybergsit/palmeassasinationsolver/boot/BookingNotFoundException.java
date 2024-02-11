package com.sundbybergsit.palmeassasinationsolver.boot;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingNumber) {
        super("Booking " + bookingNumber + " not found");
    }
}
