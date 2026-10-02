package net.modtale.controller.admin;

import net.modtale.service.admin.review.*;
import net.modtale.service.security.scan.RemoteReviewDiscovery;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ReviewOriginControllerTest.Config.class)
class ReviewOriginControllerTest {
 @Configuration @EnableMethodSecurity static class Config {
  @Bean ReviewOriginInventory inventory(){return mock(ReviewOriginInventory.class);}
  @Bean ReviewOriginController controller(ReviewOriginInventory inventory){return new ReviewOriginController(inventory);}
  @Bean(name="apiSecurity") Permissions permissions(){return new Permissions();}
 }
 public static class Permissions {public boolean hasAdminPermission(String permission,Authentication auth){return auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}}
 @Autowired ReviewOriginController controller;@Autowired ReviewOriginInventory inventory;
 void auth(String permission){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority(permission))));}
 @BeforeEach void setup(){reset(inventory);auth("PROJECT_REVIEW_READ");}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 @Test void permissionPrecedesValidationAndDatabaseAccess() {
  for(String p:List.of("PROJECT_REVIEW_DECIDE","PROJECT_VERSION_RESCAN","ROLE_USER","ROLE_ANONYMOUS")){auth(p);assertThrows(AccessDeniedException.class,()->controller.read("bad",0));}verifyNoInteractions(inventory);
 }
 @Test void cursorScopeAndCanonicalEncodingAreStrict() {
  for(Object id:List.of("a",new ObjectId("abcdefabcdefabcdefabcdef")))for(boolean after:List.of(false,true)){
   var cursor=new RemoteReviewDiscovery.Cursor(id,after?0:12,after);assertEquals(cursor,ReviewOriginCursor.decode(ReviewOriginCursor.encode(cursor)));
   assertThrows(IllegalArgumentException.class,()->ReviewOriginCursor.decode(ReviewStateDiagnosticCursor.encode(cursor)));
   assertThrows(IllegalArgumentException.class,()->ReviewStateDiagnosticCursor.decode(ReviewOriginCursor.encode(cursor)));
  }
  for(String token:List.of("o1.a.1.s.1.YQ","o1.v.1.s.00.YQ","o1.v.1.s.0.YQ==","o1.v.1.o.0.YQ","o1.v.1.s.0.YR"))assertThrows(IllegalArgumentException.class,()->ReviewOriginCursor.decode(token));
 }
 @Test void invalidHttpParametersNeverReadRecords()throws Exception {
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
  for(String limit:List.of("0","65","no","2147483648"))mvc.perform(get("/api/v1/admin/verification/origins/page").param("limit",limit)).andExpect(status().isBadRequest());
  for(String cursor:List.of("bad","d1.v.1.s.0.YQ","1.s.0.YQ"))mvc.perform(get("/api/v1/admin/verification/origins/page").param("cursor",cursor)).andExpect(status().isBadRequest());verifyNoInteractions(inventory);
 }
 @Test void emptyPageKeepsContinuationAndDoesNotClaimCompletion()throws Exception {
  var next=new RemoteReviewDiscovery.Cursor("a",25,false);when(inventory.page(null,25)).thenReturn(new ReviewOriginInventory.Page(List.of(),next,25));
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
  mvc.perform(get("/api/v1/admin/verification/origins/page")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
   .andExpect(jsonPath("$.scope").value("RETAINED_REVIEW_ORIGINS")).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.nextCursor").value(ReviewOriginCursor.encode(next))).andExpect(jsonPath("$.examinedSlots").value(25));
 }
 @Test void recordsPreserveBsonIdentityAndAmbiguityWithoutOriginPayload()throws Exception {
  String hex="abcdefabcdefabcdefabcdef";
  when(inventory.page(null,25)).thenReturn(new ReviewOriginInventory.Page(List.of(new ReviewOriginInventory.Item(new ObjectId(hex),2,null,true,null,null,ReviewOriginInventory.OriginState.UNREADABLE_BINDING)),null,3));
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
  mvc.perform(get("/api/v1/admin/verification/origins/page")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].position.projectIdType").value("OBJECT_ID"))
   .andExpect(jsonPath("$.items[0].position.projectId").value(hex)).andExpect(jsonPath("$.items[0].ambiguousVersion").value(true))
   .andExpect(jsonPath("$.items[0].originState").value("UNREADABLE_BINDING")).andExpect(jsonPath("$.items[0].deploymentId").doesNotExist()).andExpect(jsonPath("$.items[0].callerScope").doesNotExist());
 }
 @Test void databaseFailuresHaveGenericOperationalResponse()throws Exception {
  when(inventory.page(null,25)).thenThrow(new IllegalStateException("private connection details"));
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
  var response=mvc.perform(get("/api/v1/admin/verification/origins/page")).andExpect(status().isServiceUnavailable()).andReturn().getResponse();assertFalse(response.getContentAsString().contains("private connection details"));
 }
}
