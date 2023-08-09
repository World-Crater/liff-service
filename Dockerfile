FROM nightscape/scala-mill:latest AS build
WORKDIR /app
COPY . .
RUN mill app.assembly

FROM openjdk:15
WORKDIR /app
COPY --from=build /app/out/app/assembly/dest/out.jar /app/main.jar
CMD ["java","-cp","/app/main.jar","app.MinimalApplication"]