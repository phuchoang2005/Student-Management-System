import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext.jsx';
import RequireAuth from './auth/RequireAuth.jsx';
import { landingRoute } from './auth/permissions.js';
import AppShell from './components/AppShell.jsx';

import LoginPage from './pages/LoginPage.jsx';
import ChangePasswordPage from './pages/ChangePasswordPage.jsx';
import StudentListPage from './pages/students/StudentListPage.jsx';
import StudentDetailPage from './pages/students/StudentDetailPage.jsx';
import BookListPage from './pages/books/BookListPage.jsx';
import BookDetailPage from './pages/books/BookDetailPage.jsx';
import CourseListPage from './pages/courses/CourseListPage.jsx';
import CourseDetailPage from './pages/courses/CourseDetailPage.jsx';
import EnrollmentPage from './pages/enrollments/EnrollmentPage.jsx';
import MyBooksAndCoursesPage from './pages/me/MyBooksAndCoursesPage.jsx';
import StaffAccountsPage from './pages/staff/StaffAccountsPage.jsx';

/** Sends each role to its own landing page, since no single route is visible to all five. */
function HomeRedirect() {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  if (session.mustChangePassword) return <Navigate to="/change-password" replace />;
  return <Navigate to={landingRoute(session.role)} replace />;
}

/** `capability` on each route is checked by RequireAuth against the §5 matrix. */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        {/* No capability gate: every authenticated role can change its own password, and this is
            the one route a must-change-password user is allowed to reach. */}
        <Route path="/change-password" element={<ChangePasswordPage />} />

        <Route
          path="/students"
          element={
            <RequireAuth capability="domain:read">
              <StudentListPage />
            </RequireAuth>
          }
        />
        <Route
          path="/students/:code"
          element={
            <RequireAuth capability="domain:read">
              <StudentDetailPage />
            </RequireAuth>
          }
        />

        <Route
          path="/books"
          element={
            <RequireAuth capability="domain:read">
              <BookListPage />
            </RequireAuth>
          }
        />
        <Route
          path="/books/:isbn"
          element={
            <RequireAuth capability="domain:read">
              <BookDetailPage />
            </RequireAuth>
          }
        />

        <Route
          path="/courses"
          element={
            <RequireAuth capability="domain:read">
              <CourseListPage />
            </RequireAuth>
          }
        />
        <Route
          path="/courses/:code"
          element={
            <RequireAuth capability="domain:read">
              <CourseDetailPage />
            </RequireAuth>
          }
        />

        <Route
          path="/enrollments"
          element={
            <RequireAuth capability="domain:read">
              <EnrollmentPage />
            </RequireAuth>
          }
        />

        <Route
          path="/me"
          element={
            <RequireAuth capability="me:read">
              <MyBooksAndCoursesPage />
            </RequireAuth>
          }
        />

        <Route
          path="/staff-accounts"
          element={
            <RequireAuth capability="staff:manage">
              <StaffAccountsPage />
            </RequireAuth>
          }
        />
      </Route>

      <Route path="/" element={<HomeRedirect />} />
      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
