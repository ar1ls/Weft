package com.github.ar1ls.weft.runtime;

import com.github.ar1ls.weft.api.Provider;
import com.github.ar1ls.weft.api.exception.BeanCreationException;
import com.github.ar1ls.weft.definition.*;
import com.github.ar1ls.weft.runtime.factory.ConstructorBeanFactory;
import com.github.ar1ls.weft.runtime.fixtures.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BeanCreatorTest {

    @Test
    void createsBeanWithPublicNoArgConstructor() {
        var creator = new BeanCreator();
        var bean = creator.create(SimpleBean.class);
        assertNotNull(bean);
        assertEquals(SimpleBean.class, bean.getClass());
    }

    @Test
    void reportsMissingPublicNoArgConstructor() {
        var creator = new BeanCreator();
        var exception = assertThrows(BeanCreationException.class, () -> creator.create(NoDefaultConstructorBean.class));
        assertTrue(exception.getMessage().contains(NoDefaultConstructorBean.class.getName()));
        assertInstanceOf(NoSuchMethodException.class, exception.getCause());
    }

    @Test
    void createsNewInstanceOnEachCall() {
        var creator = new BeanCreator();
        var first = creator.create(SimpleBean.class);
        var second = creator.create(SimpleBean.class);
        assertNotSame(first, second);
    }

    @Test
    void preservesConstructorFailureCause() {
        var creator = new BeanCreator();
        var exception = assertThrows(BeanCreationException.class, () -> creator.create(ThrowingConstructorBean.class));
        assertTrue(exception.getMessage().contains(ThrowingConstructorBean.class.getName()));
        var cause = assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("Constructor probe", cause.getMessage());
    }

    @Test
    void createsBeanUsingDefinitionFactory() {
        var creator = new BeanCreator();
        var definition = BeanDefinitions.of(Object.class, new ConstructorBeanFactory(SimpleBean.class));

        var bean = creator.create(definition);

        assertInstanceOf(SimpleBean.class, bean);
    }

    @Test
    void rejectsIncompatibleFactoryResult() {
        var creator = new BeanCreator();
        var definition = BeanDefinitions.of(SimpleBean.class, new ConstructorBeanFactory(Object.class));

        var exception = assertThrows(BeanCreationException.class, () -> creator.create(definition));

        assertTrue(exception.getMessage().contains(definition.key().toString()));
    }

    @Test
    void rejectsNullFactoryResult() {
        var creator = new BeanCreator();
        var definition = BeanDefinitions.of(SimpleBean.class, new TestBeanFactory(List.of(), () -> null));

        var exception = assertThrows(BeanCreationException.class, () -> creator.create(definition));

        assertTrue(exception.getMessage().contains(definition.key().toString()));
    }

    @Test
    void preservesConstructorFailureCauseForDefinition() {
        var creator = new BeanCreator();
        var definition = BeanDefinitions.of(ThrowingConstructorBean.class);

        var exception = assertThrows(BeanCreationException.class, () -> creator.create(definition));

        assertTrue(exception.getMessage().contains(definition.key().toString()));
        var cause = assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("Constructor probe", cause.getMessage());
    }

    @Test
    void preservesDirectFactoryFailureCause() {
        var creator = new BeanCreator();
        var cause = new IllegalStateException("Factory probe");
        var factory = new TestBeanFactory(List.of(), () -> {
            throw cause;
        });
        var definition = BeanDefinitions.of(SimpleBean.class, factory);

        var exception = assertThrows(BeanCreationException.class, () -> creator.create(definition));

        assertSame(cause, exception.getCause());
        assertTrue(exception.getMessage().contains(definition.key().toString()));
    }

    @Test
    void requiresResolvedArgumentsBeforeCallingDependentFactory() {
        var creator = new BeanCreator();
        var calls = new AtomicInteger();
        var dependency = new DependencyDescriptor(TypeRef.of(SimpleBean.class), "", DependencyKind.DIRECT, new InjectionPoint("test factory argument"));
        var factory = new TestBeanFactory(List.of(dependency), () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        var definition = BeanDefinitions.of(SimpleBean.class, factory);

        assertThrows(BeanCreationException.class, () -> creator.create(definition));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsGenericTypeBeforeCallingFactory() throws Exception {
        var creator = new BeanCreator();
        var calls = new AtomicInteger();
        var factory = new TestBeanFactory(List.of(), () -> {
            calls.incrementAndGet();
            return List.of();
        });
        var base = BeanDefinitions.of(List.class, factory);
        var type = TypeRef.of(List.class.getMethod("iterator").getGenericReturnType());
        var definition = new BeanDefinition(base.key(), type, base.name(), base.scope(), base.lazy(), base.primary(), base.source(), base.factory());

        assertThrows(BeanCreationException.class, () -> creator.create(definition));
        assertEquals(0, calls.get());
    }

    @Test
    void createsDefinitionWithResolvedArguments() {
        var creator = new BeanCreator();
        var definition = BeanDefinitions.of(ConstructorDependentBean.class);
        var dependency = new SimpleBean();

        var bean = (ConstructorDependentBean) creator.create(definition, new Object[]{dependency});

        assertSame(dependency, bean.dependency());
    }

    @Test
    void rejectsWrongArgumentTypeBeforeCallingFactory() {
        var creator = new BeanCreator();
        var calls = new AtomicInteger();
        var dependency = new DependencyDescriptor(TypeRef.of(SimpleBean.class), "", DependencyKind.DIRECT,
                new InjectionPoint("test argument"));
        var factory = new TestBeanFactory(List.of(dependency), () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        });
        var definition = BeanDefinitions.of(SimpleBean.class, factory);

        assertThrows(BeanCreationException.class, () -> creator.create(definition, new Object[]{new Object()}));
        assertThrows(BeanCreationException.class, () -> creator.create(definition, new Object[]{null}));
        assertEquals(0, calls.get());
    }

    @Test
    void requiresResolvedArgumentsForSelectedInjectConstructor() {
        var creator = new BeanCreator();

        assertThrows(BeanCreationException.class, () -> creator.create(InjectedConstructorBean.class));
    }

    @Test
    void acceptsProviderWrapperWithoutInvokingIt() {
        var creator = new BeanCreator();
        var calls = new AtomicInteger();
        Provider<SimpleBean> provider = () -> {
            calls.incrementAndGet();
            return new SimpleBean();
        };
        var definition = BeanDefinitions.of(ProviderDependentBean.class);

        var bean = (ProviderDependentBean) creator.create(definition, new Object[]{provider});

        assertSame(provider, bean.provider());
        assertEquals(0, calls.get());
        assertThrows(BeanCreationException.class, () -> creator.create(definition, new Object[]{new SimpleBean()}));
    }

}
