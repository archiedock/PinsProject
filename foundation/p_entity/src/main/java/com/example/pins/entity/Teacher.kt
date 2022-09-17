package com.example.pins.entity

import com.example.pins.interfaces.Person
import com.example.pins.usecase.Greeting

class Teacher : Person {

    override fun sayHello() {
        println("hello teacher")
    }
}