package dev.deepdive.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReflectionTest {

    // -------------------------------------------------------------------------
    // 문자열로 클래스 동적 로드
    // -------------------------------------------------------------------------

    @Test
    void 문자열로_클래스를_로드할_수_있다() throws Exception {
        Class<?> clazz = Class.forName("dev.deepdive.sandbox.Person");

        assertThat(clazz).isEqualTo(Person.class);
        assertThat(clazz.getSimpleName()).isEqualTo("Person");
    }

    @Test
    void 동적으로_로드한_클래스로_인스턴스를_만들_수_있다() throws Exception {
        Class<?> clazz = Class.forName("dev.deepdive.sandbox.Person");
        Constructor<?> constructor = clazz.getDeclaredConstructor(String.class, int.class);

        Object person = constructor.newInstance("홍길동", 30);

        assertThat(person).isInstanceOf(Person.class);
        assertThat(((Person) person).name).isEqualTo("홍길동");
    }

    @Test
    void 존재하지_않는_클래스를_로드하면_예외가_발생한다() {
        assertThatThrownBy(() -> Class.forName("dev.deepdive.sandbox.NoSuchClass"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // 클래스 메타데이터
    // -------------------------------------------------------------------------

    @Test
    void 클래스_이름과_패키지를_가져올_수_있다() {
        Class<Person> clazz = Person.class;

        assertThat(clazz.getName()).isEqualTo("dev.deepdive.sandbox.Person");
        assertThat(clazz.getSimpleName()).isEqualTo("Person");
        assertThat(clazz.getPackageName()).isEqualTo("dev.deepdive.sandbox");
    }

    @Test
    void 선언된_필드_목록을_가져올_수_있다() {
        // getDeclaredFields(): private 포함 이 클래스에 선언된 모든 필드
        // getFields():         public 필드만 (상속 포함)
        List<String> fieldNames = Arrays.stream(Person.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertThat(fieldNames).containsExactlyInAnyOrder("name", "age", "species");
    }

    @Test
    void 선언된_메서드_목록을_가져올_수_있다() {
        List<String> methodNames = Arrays.stream(Person.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(methodNames).containsExactlyInAnyOrder("greet", "secret", "getSpecies");
    }

    @Test
    void public_생성자로_인스턴스를_만들_수_있다() throws Exception {
        Constructor<Person> constructor = Person.class.getDeclaredConstructor(String.class, int.class);

        Person person = constructor.newInstance("홍길동", 30);

        assertThat(person.name).isEqualTo("홍길동");
    }

    @Test
    void private_생성자에_접근해서_인스턴스를_만들_수_있다() throws Exception {
        Constructor<Person> constructor = Person.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true); // private 접근 허용

        Person person = constructor.newInstance("익명");

        assertThat(person.name).isEqualTo("익명");
    }

    @Test
    void 생성자의_접근제어자를_확인할_수_있다() throws Exception {
        Constructor<Person> publicCtor = Person.class.getDeclaredConstructor(String.class, int.class);
        Constructor<Person> privateCtor = Person.class.getDeclaredConstructor(String.class);

        assertThat(Modifier.isPublic(publicCtor.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(privateCtor.getModifiers())).isTrue();
    }

    @Test
    void public_필드를_읽고_쓸_수_있다() throws Exception {
        Person person = new Person("홍길동", 30);
        Field nameField = Person.class.getDeclaredField("name");

        nameField.set(person, "임꺽정");

        assertThat(nameField.get(person)).isEqualTo("임꺽정");
    }

    @Test
    void private_필드를_읽고_쓸_수_있다() throws Exception {
        Person person = new Person("홍길동", 30);
        Field ageField = Person.class.getDeclaredField("age");
        ageField.setAccessible(true); // private 접근 허용

        int age = (int) ageField.get(person);
        assertThat(age).isEqualTo(30);

        ageField.set(person, 99);
        assertThat(ageField.get(person)).isEqualTo(99);
    }

    @Test
    void static_필드를_읽고_쓸_수_있다() throws Exception {
        Field speciesField = Person.class.getDeclaredField("species");
        speciesField.setAccessible(true);

        // static 필드는 인스턴스 없이 null 전달
        String original = (String) speciesField.get(null);
        assertThat(original).isEqualTo("Human");

        speciesField.set(null, "Homo Sapiens");
        assertThat(speciesField.get(null)).isEqualTo("Homo Sapiens");

        speciesField.set(null, original); // 원복
    }

    @Test
    void public_메서드를_호출할_수_있다() throws Exception {
        Person person = new Person("홍길동", 30);
        Method greet = Person.class.getDeclaredMethod("greet");

        String result = (String) greet.invoke(person);

        assertThat(result).isEqualTo("안녕하세요, 홍길동입니다.");
    }

    @Test
    void private_메서드를_호출할_수_있다() throws Exception {
        Person person = new Person("홍길동", 30);
        Method secret = Person.class.getDeclaredMethod("secret");
        secret.setAccessible(true); // private 접근 허용

        String result = (String) secret.invoke(person);

        assertThat(result).isEqualTo("홍길동의 비밀 나이: 30");
    }

    @Test
    void private_static_메서드를_호출할_수_있다() throws Exception {
        Method getSpecies = Person.class.getDeclaredMethod("getSpecies");
        getSpecies.setAccessible(true);

        // static 메서드는 인스턴스 없이 null 전달
        String result = (String) getSpecies.invoke(null);

        assertThat(result).isEqualTo("Human");
    }
}
