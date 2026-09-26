package vn.ptit.snapvocab.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "shop_items")
public class ShopItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false, length = 50)
	private String type;

	@Column(nullable = false)
	private Long price;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}


