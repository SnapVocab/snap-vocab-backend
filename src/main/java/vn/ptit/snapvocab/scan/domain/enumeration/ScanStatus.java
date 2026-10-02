package vn.ptit.snapvocab.scan.domain.enumeration;

import java.util.EnumSet;
import java.util.Set;

public enum ScanStatus {
	PENDING,
	PROCESSING,
	DONE,
	FAILED;

	/** Scans that use up daily quota: everything except FAILED, so a failure never costs the user a scan. */
	public static final Set<ScanStatus> COUNTED = EnumSet.of(PENDING, PROCESSING, DONE);

	/** Not finished yet; the sweeper fails these when they outlive any plausible run time. */
	public static final Set<ScanStatus> ACTIVE = EnumSet.of(PENDING, PROCESSING);
}

