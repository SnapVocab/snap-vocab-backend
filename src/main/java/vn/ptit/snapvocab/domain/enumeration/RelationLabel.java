package vn.ptit.snapvocab.domain.enumeration;

public enum RelationLabel implements Labeled {

    S("s", "Đồng nghĩa"),
    A("a", "Trái nghĩa"),
    D("d", "Từ phái sinh"),
    R("r", "Liên quan");

    private final String code;
    private final String label;

    RelationLabel(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
