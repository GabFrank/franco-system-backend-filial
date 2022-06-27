package com.franco.dev.security;

import com.franco.dev.domain.personas.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtUser{

     Long id;

     String nickname;

     String password;

     String roles;

     public void setRoles(List<Role> roles){
          StringBuilder strb = new StringBuilder();
          for(Role r : roles){
               if(strb.length()>0){
                    strb.append(",");
               }
               strb.append(r.getNombre());
          }
     }
}
