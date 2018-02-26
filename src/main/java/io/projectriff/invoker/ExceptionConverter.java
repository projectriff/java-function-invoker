package io.projectriff.invoker;

import io.grpc.Status;

import java.io.FileNotFoundException;
import java.security.AccessControlException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for converting exceptions to gRPC status values.
 */
final class ExceptionConverter {
	static Status createStatus(Throwable t) {
		return Status.fromCode(determineCode(t))
				.withDescription(t.getMessage())
				.withCause(t);
	}

	private static Status.Code determineCode(Throwable t) {
		if (t instanceof CancellationException) return Status.Code.CANCELLED;

		if (t instanceof IllegalArgumentException) return Status.Code.INVALID_ARGUMENT;

		if (t instanceof TimeoutException) return Status.Code.DEADLINE_EXCEEDED;

		if (t instanceof ClassNotFoundException) return Status.Code.NOT_FOUND;
		if (t instanceof FileNotFoundException) return Status.Code.NOT_FOUND;
		if (t instanceof NoSuchFieldException) return Status.Code.NOT_FOUND;
		if (t instanceof NoSuchMethodException) return Status.Code.NOT_FOUND;

		if (t instanceof AccessControlException) return Status.Code.PERMISSION_DENIED;

		if (t instanceof IllegalStateException) return Status.Code.FAILED_PRECONDITION;

		if (t instanceof InterruptedException) return Status.Code.ABORTED;

		if (t instanceof ArrayIndexOutOfBoundsException) return Status.Code.OUT_OF_RANGE;
		if (t instanceof StringIndexOutOfBoundsException) return Status.Code.OUT_OF_RANGE;

		if (t instanceof UnsupportedOperationException) return Status.Code.UNIMPLEMENTED;

		if (t instanceof RuntimeException) return Status.Code.INTERNAL;

		return Status.Code.UNKNOWN;
	}
}
