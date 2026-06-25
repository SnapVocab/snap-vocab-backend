package vn.ptit.snapvocab.domain.enumeration;

public enum PosLabel implements Labeled {

    A("A", "Tính từ"),
    C("C", "Liên từ"),
    D("D", "Phó từ"),
    E("E", "Giới từ"),
    I("I", "Tình thái từ"),
    M("M", "Lượng từ"),
    N("N", "Danh từ"),
    O("O", "Thán từ"),
    P("P", "Đại từ"),
    R("R", "Trạng từ"),
    S("S", "Từ đặc biệt"),
    V("V", "Động từ"),
    X("X", "Từ phụ trợ / khác"),
    Z("Z", "Hậu tố");

    private final String code;
    private final String label;

    PosLabel(String code, String label) {
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
