package vn.ptit.snapvocab.domain.enumeration;

public enum SubPosLabel implements Labeled {

    A("A", "Tính từ nói chung"),
    A0("A0", "Tính từ chỉ số lượng, trạng thái tổng quát"),
    Ai("Ai", "Tính từ chỉ khả năng, thuộc tính nội tại"),
    Ao("Ao", "Tính từ chỉ âm thanh, cảm giác"),
    Ap("Ap", "Tính từ phổ biến"),
    Ar("Ar", "Tính từ chỉ mức độ, cường độ"),
    Ax("Ax", "Tính từ đặc biệt"),
    Vi("Vi", "Động từ nội"),
    Vm("Vm", "Động từ phương pháp"),
    Vs("Vs", "Động từ không chuyển"),
    Vt("Vt", "Động từ chuyển"),
    Vu("Vu", "Động từ đặc biệt/thành ngữ"),
    N("N", "Danh từ nói chung"),
    Na("Na", "Danh từ trừu tượng"),
    Nc("Nc", "Danh từ chỉ loại"),
    Ng("Ng", "Danh từ giống loài"),
    Nl("Nl", "Danh từ chỉ vị trí"),
    Np("Np", "Danh từ riêng"),
    Nt("Nt", "Danh từ chỉ vật, hiện tượng"),
    Nu("Nu", "Danh từ đơn vị"),
    Nx("Nx", "Danh từ chuyên biệt"),
    Pd("Pd", "Đại từ chỉ định"),
    Pi("Pi", "Đại từ nhân xưng"),
    Pp("Pp", "Đại từ sở hữu"),
    Pq("Pq", "Đại từ nghi vấn"),
    C("C", "Liên từ"),
    D("D", "Phó từ"),
    E("E", "Giới từ"),
    I("I", "Thán từ"),
    Mc("Mc", "Lượng từ đơn vị"),
    Mo("Mo", "Lượng từ số lượng"),
    O("O", "Từ tình thái"),
    R("R", "Trạng từ"),
    S("S", "Từ đặc biệt"),
    X("X", "Tổ hợp từ đặc biệt"),
    XX("XX", "Từ lỗi/dữ liệu không xác định"),
    Z("Z", "Hậu tố");

    private final String code;
    private final String label;

    SubPosLabel(String code, String label) {
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
