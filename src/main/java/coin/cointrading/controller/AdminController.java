package coin.cointrading.controller;

import coin.cointrading.domain.Role;
import coin.cointrading.domain.User;
import coin.cointrading.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;


    @GetMapping("/users")
    public String userList(
            @RequestParam(value = "field", required = false, defaultValue = "userId") String field,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model
    ) {
        List<User> users;

        if (keyword == null || keyword.isEmpty()) {
            users = adminService.getAllUsers();
        } else {
            users = adminService.searchUsers(field, keyword);
        }

        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("field", field);
        return "adminPage";
    }


    @PatchMapping("/users/{id}/role")
    public void updateUserRole(@PathVariable Long id, @RequestParam Role role) {
        adminService.changeRole(id, role);
    }
}
