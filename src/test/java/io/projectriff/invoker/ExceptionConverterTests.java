package io.projectriff.invoker;

import java.io.FileNotFoundException;
import java.security.AccessControlException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import io.grpc.Status;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionConverterTests {

	private static final String TEST_MESSAGE = "test message";

	@Test
	public void cancelled() {
		checkStatus(new CancellationException(TEST_MESSAGE), Status.Code.CANCELLED);
	}

	@Test
	public void unknown() {
		checkStatus(new TestException(TEST_MESSAGE), Status.Code.UNKNOWN);
	}

	@Test
	public void invalidArgument() {
		checkStatus(new IllegalArgumentException(TEST_MESSAGE),
				Status.Code.INVALID_ARGUMENT);
	}

	@Test
	public void deadlineExceeded() {
		checkStatus(new TimeoutException(TEST_MESSAGE), Status.Code.DEADLINE_EXCEEDED);
	}

	@Test
	public void notFound() {
		checkStatus(new ClassNotFoundException(TEST_MESSAGE), Status.Code.NOT_FOUND);
		checkStatus(new FileNotFoundException(TEST_MESSAGE), Status.Code.NOT_FOUND);
		checkStatus(new NoSuchFieldException(TEST_MESSAGE), Status.Code.NOT_FOUND);
		checkStatus(new NoSuchMethodException(TEST_MESSAGE), Status.Code.NOT_FOUND);
	}

	@Test
	public void alreadyExists() {
		// No exceptions map to this code.
	}

	@Test
	public void permissionDenied() {
		checkStatus(new AccessControlException(TEST_MESSAGE),
				Status.Code.PERMISSION_DENIED);
	}

	@Test
	public void resourceExhausted() {
		// No exceptions map to this code.
	}

	@Test
	public void failedPrecondition() {
		checkStatus(new IllegalStateException(TEST_MESSAGE),
				Status.Code.FAILED_PRECONDITION);
	}

	@Test
	public void aborted() {
		checkStatus(new InterruptedException(TEST_MESSAGE), Status.Code.ABORTED);
	}

	@Test
	public void outOfRange() {
		checkStatus(new ArrayIndexOutOfBoundsException(TEST_MESSAGE),
				Status.Code.OUT_OF_RANGE);
		checkStatus(new StringIndexOutOfBoundsException(TEST_MESSAGE),
				Status.Code.OUT_OF_RANGE);
	}

	@Test
	public void unimplemented() {
		checkStatus(new UnsupportedOperationException(TEST_MESSAGE),
				Status.Code.UNIMPLEMENTED);
	}

	@Test
	public void internal() {
		checkStatus(new RuntimeException(TEST_MESSAGE), Status.Code.INTERNAL);
		checkStatus(new NullPointerException(TEST_MESSAGE), Status.Code.INTERNAL);
	}

	@Test
	public void unavailable() {
		// No exceptions map to this code.
	}

	@Test
	public void dataLoss() {
		// No exceptions map to this code.
	}

	@Test
	public void unauthenticated() {
		// No exceptions map to this code.
	}

	private void checkStatus(Throwable cause, Status.Code expectedCode) {
		Status s = ExceptionConverter.createStatus(cause);
		assertThat(s.getCode()).isEqualTo(expectedCode);
		assertThat(s.getDescription()).isEqualTo(TEST_MESSAGE);
		assertThat(s.getCause()).isEqualTo(cause);
	}

	@SuppressWarnings("serial")
	private static class TestException extends Throwable {
		TestException(String message) {
			super(message);
		}
	}
}
