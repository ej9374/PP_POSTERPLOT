package uniVerse.posterPlot.service;

import com.google.auth.oauth2.ServiceAccountCredentials;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uniVerse.posterPlot.dto.ReceiveFlaskResponseDto;
import uniVerse.posterPlot.dto.SendToFlaskRequestDto;
import uniVerse.posterPlot.entity.AiStoryEntity;
import uniVerse.posterPlot.entity.MovieListEntity;
import uniVerse.posterPlot.entity.UserEntity;
import uniVerse.posterPlot.repository.AiStoryRepository;
import uniVerse.posterPlot.repository.MovieListRepository;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Blob;
import uniVerse.posterPlot.repository.UserRepository;

// ❌ java.net.http.HttpClient 제거!
import reactor.netty.http.client.HttpClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class MovieService {

    private final MovieListRepository movieListRepository;
    private final AiStoryRepository aiStoryRepository;
    private final UserRepository userRepository;
    private final String bucketName = "posterplot-movie-image";
    private Storage storage;

    {
        try (InputStream inputStream = getClass().getResourceAsStream("/gcp-keys/posterplot-key.json")) {
            if (inputStream == null) {
                throw new RuntimeException("gcp-keys/posterplot-key.json 리소스를 찾을 수 없습니다.");
            }

            storage = StorageOptions.newBuilder()
                    .setCredentials(ServiceAccountCredentials.fromStream(inputStream))
                    .build()
                    .getService();
        } catch (IOException e) {
            throw new RuntimeException("GCP 인증 키 파일을 로드할 수 없습니다: " + e.getMessage(), e);
        }
    }

    // ... (uploadMovieImage, uploadToGCP, saveMovieImage 메서드는 기존과 동일하므로 생략) ...
    @Transactional
    public Integer uploadMovieImage(UserEntity user, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (files.size() < 2) {
            throw new IllegalArgumentException("최소 2개의 이미지를 업로드해야 합니다.");
        }
        String movie1stPath = uploadToGCP(files.get(0));
        String movie2ndPath = uploadToGCP(files.get(1));
        MovieListEntity movieList = saveMovieImage(user, movie1stPath, movie2ndPath);
        return movieList.getMovieListId();
    }

    @Transactional
    public String uploadToGCP(MultipartFile file){
        try {String originalFilename = file.getOriginalFilename();
            String uuid = UUID.randomUUID().toString();
            String newFilename = uuid+"_"+originalFilename;
            BlobId blobId = BlobId.of(bucketName, newFilename);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
            Blob blob = storage.create(blobInfo, file.getBytes());
            return blob.getMediaLink();
        } catch(IOException e){
            throw new RuntimeException("파일 업로드 중 오류 발생", e);
        }
    }

    @Transactional
    public MovieListEntity saveMovieImage(UserEntity user, String movie1stPath, String movie2ndPath){
        MovieListEntity movieList = new MovieListEntity(user, movie1stPath, movie2ndPath);
        movieListRepository.save(movieList);
        return movieList;
    }
    // ... (여기까지 기존 동일) ...


    // 👇 여기가 수정된 부분입니다!
    @Transactional
    public Integer sendMovieListToFlask(Integer movieListId) {

        // 타임아웃 설정을 위한 HttpClient (import 주의: reactor.netty.http.client.HttpClient)
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5))
                                .addHandlerLast(new WriteTimeoutHandler(5)));

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:5000")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient)) // ✅ 타임아웃 적용
                .build();

        log.info("sendMovieListToFlask 호출됨. movieListId={}", movieListId);

        MovieListEntity movieList = movieListRepository.findByMovieListId(movieListId);
        if (movieList == null) {
            throw new RuntimeException("해당 movieListId에 대한 데이터가 없습니다.");
        }

        SendToFlaskRequestDto flaskRequestDto = new SendToFlaskRequestDto(
                movieListId,
                List.of(movieList.getMovie1stPath(), movieList.getMovie2ndPath())
        );

        try {
            ReceiveFlaskResponseDto flaskResponseDto = webClient.post()
                    .uri("/generate_story")
                    .bodyValue(flaskRequestDto)
                    .retrieve()
                    // ✅ 수정 1: onStatus 내부 block() 제거 -> flatMap으로 비동기 처리
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("❌ Flask API 호출 실패: 코드={}, 내용={}", response.statusCode(), errorBody);
                                        return Mono.error(new RuntimeException("Flask API 오류: " + errorBody));
                                    })
                    )
                    .bodyToMono(ReceiveFlaskResponseDto.class)
                    // ✅ 수정 2: 재시도 횟수 현실화 (30회 -> 3회, 15초 -> 2초)
                    .retryWhen(reactor.util.retry.Retry.fixedDelay(3, Duration.ofSeconds(2))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    new RuntimeException("🚨 Flask API 재시도 실패: 최대 시도 횟수 초과")))
                    .block(); // 최종 결과 받을 때만 block() 사용 (이건 OK)

            if (flaskResponseDto == null) {
                throw new RuntimeException("Flask API 응답이 비어있습니다.");
            }

            log.info("API 응답 수신 완료: story={}", flaskResponseDto.getGeneratedStory());
            return saveAiStory(movieList, flaskResponseDto.getGeneratedStory());

        } catch (Exception e) {
            log.error("🚨 Flask API 호출 중 예외 발생: {}", e.getMessage());
            throw new RuntimeException("Flask API 호출 실패: " + e.getMessage(), e);
        }
    }

    public Integer saveAiStory(MovieListEntity movieList, String story) {
        AiStoryEntity aiStory = new AiStoryEntity(story, movieList);
        aiStoryRepository.save(aiStory);
        return aiStory.getAiStoryId();
    }

    @Transactional
    public AiStoryEntity getAiStory(Integer aiStoryId) {
        AiStoryEntity aiStory = aiStoryRepository.findAiStoryById(aiStoryId);
        if (aiStory == null){
            throw new RuntimeException("Ai Story를 찾을 수 없습니다.");
        }
        return aiStory;
    }
}