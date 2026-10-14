package com.b2c.flash_sale_b2c_UTC2.image.repository;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class ImageRepositoryIT extends AbstractPostgresIT {

    @Autowired private ImageRepository imageRepository;
    @PersistenceContext private EntityManager em;

    private Image persistImage(ImageOwnerType ownerType, Long ownerId, boolean isPrimary, int order, String status) {
        Image i = Image.builder()
                .ownerType(ownerType).ownerId(ownerId)
                .url("http://x.com/" + ownerId + "/" + order + ".jpg")
                .cloudinaryPublicId("pub-" + ownerId + "-" + order)
                .isPrimary(isPrimary).displayOrder(order).status(status)
                .build();
        em.persist(i);
        em.flush();
        return i;
    }

    @Test
    @DisplayName("findByOwnerTypeAndOwnerIdAndStatusOrderByDisplayOrderAsc returns ACTIVE ordered")
    void findByOwner_OrdersByDisplayOrder() {
        persistImage(ImageOwnerType.PRODUCT, 1L, false, 2, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 1L, false, 1, "ACTIVE");

        List<Image> result = imageRepository.findByOwnerTypeAndOwnerIdAndStatusOrderByDisplayOrderAsc(
                ImageOwnerType.PRODUCT, 1L, "ACTIVE");

        assertEquals(3, result.size());
        assertEquals(0, result.get(0).getDisplayOrder());
        assertEquals(1, result.get(1).getDisplayOrder());
        assertEquals(2, result.get(2).getDisplayOrder());
    }

    @Test
    @DisplayName("findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus returns primary")
    void findPrimary_Success() {
        persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 1L, false, 1, "ACTIVE");

        Optional<Image> primary = imageRepository.findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus(
                ImageOwnerType.PRODUCT, 1L, "ACTIVE");

        assertTrue(primary.isPresent());
        assertTrue(primary.get().getIsPrimary());
    }

    @Test
    @DisplayName("findPrimaryImagesByOwnerIds batch load")
    void findPrimaryImagesByOwnerIds_BatchLoad() {
        persistImage(ImageOwnerType.PRODUCT, 10L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 11L, true, 0, "ACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 12L, false, 0, "ACTIVE");

        List<Image> result = imageRepository.findPrimaryImagesByOwnerIds(
                ImageOwnerType.PRODUCT, List.of(10L, 11L, 12L));

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("clearOtherPrimary resets other primary")
    void clearOtherPrimary_ResetsOthers() {
        // Persist 1 primary + 2 non-primary images for same owner.
        Image keep = persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");
        Image other1 = persistImage(ImageOwnerType.PRODUCT, 1L, false, 1, "ACTIVE");
        Image other2 = persistImage(ImageOwnerType.PRODUCT, 1L, false, 2, "ACTIVE");

        // Verify setup
        assertTrue(keep.getIsPrimary());
        assertFalse(other1.getIsPrimary());
        assertFalse(other2.getIsPrimary());

        // Simulate corrupted state: temporarily drop the partial unique index
        // to flip other1 and other2 to primary=true (this would normally be rejected).
        // @Transactional ensures the index is restored on rollback.
        em.createNativeQuery("DROP INDEX uq_images_owner_one_primary").executeUpdate();
        em.createNativeQuery("UPDATE images SET is_primary = true WHERE id IN (?, ?)")
                .setParameter(1, other1.getId())
                .setParameter(2, other2.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        // Now call clearOtherPrimary - should reset the 2 other rows
        int updated = imageRepository.clearOtherPrimary(
                ImageOwnerType.PRODUCT, 1L, keep.getId(), Instant.now());

        assertEquals(2, updated);
        em.clear();
        assertTrue(imageRepository.findById(keep.getId()).orElseThrow().getIsPrimary());
        assertFalse(imageRepository.findById(other1.getId()).orElseThrow().getIsPrimary());
        assertFalse(imageRepository.findById(other2.getId()).orElseThrow().getIsPrimary());
    }

    @Test
    @DisplayName("markCloudinaryDeleted marks deleted")
    void markCloudinaryDeleted_Success() {
        Image img = persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "ACTIVE");

        int updated = imageRepository.markCloudinaryDeleted(img.getId(), Instant.now());

        assertEquals(1, updated);
        em.clear();
        assertTrue(imageRepository.findById(img.getId()).orElseThrow().getCloudinaryDeleted());
    }

    @Test
    @DisplayName("findInactiveNotCleanedUp returns INACTIVE not deleted")
    void findInactiveNotCleanedUp_Returns() {
        persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "INACTIVE");
        persistImage(ImageOwnerType.PRODUCT, 2L, true, 0, "ACTIVE");

        List<Image> result = imageRepository.findInactiveNotCleanedUp();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getOwnerId());
    }

    @Test
    @DisplayName("findReadyForHardDelete returns INACTIVE+deleted past threshold")
    void findReadyForHardDelete_Returns() {
        Image old = persistImage(ImageOwnerType.PRODUCT, 1L, true, 0, "INACTIVE");
        em.flush();
        // Set cloudinary_deleted=true and updated_at to 31 days ago via native SQL
        // (bypasses Hibernate @PreUpdate which would reset updated_at to now())
        em.createNativeQuery(
                "UPDATE images SET cloudinary_deleted = true, updated_at = ? WHERE id = ?")
                .setParameter(1, java.sql.Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)))
                .setParameter(2, old.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        List<Image> result = imageRepository.findReadyForHardDelete(Instant.now().minus(30, ChronoUnit.DAYS));

        assertTrue(result.stream().anyMatch(i -> i.getId().equals(old.getId())));
    }
}