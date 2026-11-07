package vn.ptit.snapvocab.study.domain.dto;

import java.util.List;

public record CollectionStudyOverviewDTO(StudyCountsDTO collection, List<StudyCountsDTO> topics) {}
