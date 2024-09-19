package com.lantanagroup.link.cli;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import javax.validation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BaseShellCommand {
  @Autowired
  protected ApplicationContext applicationContext;

  @Autowired
  protected Environment env;

  protected List<Class<?>> getBeanClasses() {
    return new ArrayList<>();
  }

  private GenericBeanDefinition getBeanDef(Class<?> beanClass) {
    GenericBeanDefinition gbd = new GenericBeanDefinition();
    gbd.setBeanClass(beanClass);
    return gbd;
  }

  protected void registerBeans() {
    DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ((AnnotationConfigServletWebServerApplicationContext) this.applicationContext).getBeanFactory();

    for (Class<?> beanClass : this.getBeanClasses()) {
      String beanName = beanClass.getName().substring(0, 1).toLowerCase() + beanClass.getName().substring(1);

      if (!beanFactory.containsBeanDefinition(beanName)) {
        beanFactory.registerBeanDefinition(beanName, this.getBeanDef(beanClass));
      }
    }
  }

  protected <T> void validate(T object) {
    Validator validator;
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
    Set<ConstraintViolation<T>> violations = validator.validate(object);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
