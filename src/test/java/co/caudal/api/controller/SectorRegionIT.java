package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Regions are root sectors; the tree of sectors never gets a cycle or an orphan (HU-50). */
class SectorRegionIT extends SessionIT {

  private ResultActions patchSector(Session session, UUID sector, String body) throws Exception {
    return mvc.perform(
        patch("/api/v1/sectors/" + sector)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void aSectorMovesUnderARegionAndBecomesARegionAgain() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session admin = login(account("region.move", "BOARD_ADMIN", aqueduct));
    List<UUID> sectors =
        owner.queryForList(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? ORDER BY code", UUID.class, aqueduct);
    UUID region = sectors.get(0);
    UUID child = sectors.get(1);

    patchSector(admin, child, "{\"parent_sector_id\":\"" + region + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parent_sector_id").value(region.toString()))
        .andExpect(jsonPath("$.is_region").value(false));
    patchSector(admin, child, "{\"is_region\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parent_sector_id").doesNotExist())
        .andExpect(jsonPath("$.is_region").value(true));
  }

  @Test
  void aCycleAnOrphanAndASelfParentAreRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session admin = login(account("region.cycle", "BOARD_ADMIN", aqueduct));
    List<UUID> sectors =
        owner.queryForList(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? ORDER BY code", UUID.class, aqueduct);
    UUID region = sectors.get(0);
    UUID child = sectors.get(1);
    patchSector(admin, child, "{\"parent_sector_id\":\"" + region + "\"}")
        .andExpect(status().isOk());

    patchSector(admin, region, "{\"parent_sector_id\":\"" + child + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    patchSector(admin, region, "{\"parent_sector_id\":\"" + region + "\"}")
        .andExpect(status().isBadRequest());
    patchSector(admin, child, "{\"parent_sector_id\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isBadRequest());
    patchSector(admin, child, "{\"parent_sector_id\":\"" + region + "\",\"is_region\":true}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void aNewRegionIsARootSectorAndAnotherAqueductsSectorIsNotAParent() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID other = newAqueductWithRules();
    Session admin = login(account("region.new", "BOARD_ADMIN", aqueduct));
    UUID foreign =
        owner.queryForObject(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? LIMIT 1", UUID.class, other);

    String created =
        mvc.perform(
                post("/api/v1/sectors")
                    .header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"NORTE\",\"name\":\"Región Norte\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.is_region").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID region = UUID.fromString(created.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1"));

    patchSector(admin, region, "{\"parent_sector_id\":\"" + foreign + "\"}")
        .andExpect(status().isBadRequest());
  }
}
