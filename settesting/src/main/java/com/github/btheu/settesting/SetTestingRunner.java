package com.github.btheu.settesting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.springframework.util.CollectionUtils;

import com.github.btheu.settesting.core.impl.DefaultResultComparator;
import com.github.btheu.settesting.core.impl.DefaultTestCase;
import com.github.btheu.settesting.core.impl.InMemoryGridResultProvider;
import com.github.btheu.settesting.core.impl.InMemoryReport;
import com.github.btheu.settesting.core.impl.ThrowableResult;

public class SetTestingRunner extends Suite {

	private List<Runner> runners;

	private Class<?> testClass;

	private static final List<Runner> NO_RUNNERS = Collections.<Runner>emptyList();

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface UseCases{
		Class<? extends UseCase>[] value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface BusinessObjects{
		Class<? extends BusinessObject>[] value();
	}

	protected List<Class<? extends Factory>> factories = new ArrayList<Class<? extends Factory>>();

	protected List<Class<? extends UseCase>> usecases = new ArrayList<Class<? extends UseCase>>();

	protected List<Class<? extends BusinessObject>> bos = new ArrayList<Class<? extends BusinessObject>>();



	public SetTestingRunner(Class<?> klass) throws InitializationError {
		super(klass, NO_RUNNERS);

		this.testClass = klass;

		UseCases annotation = klass.getAnnotation(UseCases.class);

		addAll(usecases,annotation.value());

		BusinessObjects annotation2 = klass.getAnnotation(BusinessObjects.class);

		addAll(bos,annotation2.value());

		this.runners = new ArrayList<Runner>();


		final DefaultResultComparator comparator = new DefaultResultComparator();
		InMemoryGridResultProvider gridResultProvider = new InMemoryGridResultProvider();
		InMemoryReport report = new InMemoryReport();

		comparator.setGridResultProvider(gridResultProvider);
		comparator.setReport(report);

		int seq = 1;

		for (final Class<? extends UseCase> usecaseClass : usecases) {
			for (final Class<? extends BusinessObject> boClass : bos) {

				final int runnerId = seq++;
				
				this.runners.add(new Runner() {

					@Override
					public void run(RunNotifier notifier) {

						try {

							notifier.fireTestStarted(this.getDescription());

							UseCase usecase = usecaseClass.newInstance();

							BusinessObject businessObject = boClass.newInstance();

							businessObject.create();

							Result result = execute(usecase, businessObject);

							businessObject.remove();
							
							comparator.validate(result, new DefaultTestCase(usecase,businessObject));
							
							notifier.fireTestFinished(this.getDescription());
							
						} catch (InstantiationException e) {
							notifier.fireTestFailure(new Failure(this.getDescription(),e));
						} catch (IllegalAccessException e) {
							notifier.fireTestFailure(new Failure(this.getDescription(),e));
						} catch (ValidationException e) {
							notifier.fireTestFailure(new Failure(this.getDescription(),e));
						}
					}

					@Override
					public Description getDescription() {
						Description desc = Description.createTestDescription(testClass, usecaseClass.getSimpleName()+" : "+boClass.getSimpleName()+" "+runnerId);
						return desc ;
					}

					private Result execute(UseCase usecase, BusinessObject bo) {
						try {
							Result result = usecase.execute(bo);
							return result;
						} catch (Exception e) {
							return new ThrowableResult(e);
						}
					}

				});

			}
		}



	}

	@SuppressWarnings("unchecked")
	private <T> void addAll(List<Class<? extends T>> list, Class<? extends T>[] items) {
		list.addAll(CollectionUtils.arrayToList(items));
	}

	@Override
	protected List<Runner> getChildren() {
		return runners;
	}



}
